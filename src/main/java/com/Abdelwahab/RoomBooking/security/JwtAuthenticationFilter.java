package com.Abdelwahab.RoomBooking.security;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.common.lang.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-request JWT authentication and revocation gate.
 *
 * <h2>Architectural Role</h2>
 * <p>This filter sits in the Spring Security {@code FilterChain} immediately before
 * {@code UsernamePasswordAuthenticationFilter}. It is the first class in the
 * request lifecycle that sees incoming credentials, making it the correct place to:
 * <ol>
 *   <li>Extract and parse the JWT from the HttpOnly cookie</li>
 *   <li>Check the revocation blacklist (Redis) before doing any user lookup</li>
 *   <li>Populate the {@link SecurityContextHolder} so that downstream filters,
 *       interceptors, and controllers can call
 *       {@code SecurityContextHolder.getContext().getAuthentication()} reliably</li>
 * </ol>
 *
 * <h2>Cookie vs Authorization Header</h2>
 * <p>The filter reads the JWT from the {@code jwt} HttpOnly cookie rather than the
 * {@code Authorization} header. HttpOnly cookies are invisible to JavaScript —
 * {@code document.cookie} returns nothing for them. This design decision makes it
 * impossible for XSS payloads to steal the token, unlike the {@code Authorization}
 * header pattern where any injected script can call
 * {@code localStorage.getItem("token")}.
 *
 * <h2>Fail-Closed Revocation</h2>
 * <p>Any exception raised during token parsing or blacklist lookup is treated as a
 * hard authentication failure. The filter returns {@code 401 Unauthorized} immediately
 * without continuing the chain. This is the correct default for a security gate:
 * it is safer to reject a valid request due to a transient Redis outage than to grant
 * access to a revoked token because the check was skipped.
 *
 * <h2>Thread Safety</h2>
 * <p>As a {@link OncePerRequestFilter}, this runs exactly once per request on its
 * own thread. No instance state is mutated between requests. All fields are injected
 * singleton collaborators and are themselves thread-safe.
 *
 * @see JwtService
 * @see TokenBlacklistService
 * @see SecurityConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Bridge to the database: resolves an email address to the live {@link UserDetails}
     * (the {@code Guest} entity) on every authenticated request. Loading from the DB
     * — rather than trusting role claims in the token — means privilege changes
     * (demotions, account disabling) take effect on the very next request.
     */
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws IOException, ServletException {

        // ── Step 1: Extract the JWT from the HttpOnly cookie ──────────────────────
        // We read from a cookie, NOT the Authorization header. HttpOnly cookies are
        // invisible to JavaScript — document.cookie cannot expose them — making XSS
        // token theft structurally impossible regardless of injection vulnerabilities
        // elsewhere on the page.
        if (request.getCookies() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = Arrays.stream(request.getCookies())
                .filter(cookie -> "jwt".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 2: Parse the token and extract core claims ───────────────────────
        // extractUsername and extractJti both trigger a full parse-and-verify cycle
        // internally. Any structural, signature, or expiry problem throws here.
        // We catch broadly and respond 401 immediately (fail-closed: see class Javadoc).
        final String userEmail;
        final String jti;
        final String userId;
        try {
            userEmail = jwtService.extractUsername(jwt);
            jti = jwtService.extractJti(jwt);
            userId = jwtService.extractUserIdFromToken(jwt);
        } catch (Exception e) {
            log.warn("JWT parsing failed: {}", e.getMessage());
            writeUnauthorized(response, "Invalid or expired token.");
            return;
        }

        // ── Step 3: Two-tier revocation check ─────────────────────────────────────
        // Both tiers precede any user-DB lookup: a revoked token or a globally-banned
        // user must be rejected even while the underlying account row is still valid.
        // The GLOBAL ban is checked first, because a hit there short-circuits every
        // token the user holds and makes the per-token lookup unnecessary.
        //
        // A single try/catch wraps both Redis reads so the fail-closed guarantee is
        // identical for either tier: any Redis error denies access rather than
        // skipping a security gate.
        try {
            // Step 3a — GLOBAL user ban. Isolated in O(1) from the compound jti's
            // <userId> prefix; a hit invalidates all of this user's tokens at once
            // (admin ban, password change, "log out everywhere").
            if (userId != null && tokenBlacklistService.isUserBanned(userId)) {
                String reason = tokenBlacklistService.getUserBanReason(userId);
                log.warn("Request rejected: user globally banned [userId={}]. Reason: {}", userId, reason);
                writeUnauthorized(response, "Access revoked.");
                return;
            }

            // Step 3b — TOKEN-level revocation. Only reached when the user is not
            // globally banned. Rejects this one token (e.g. single-device logout).
            if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
                String reason = tokenBlacklistService.getRevocationReason(jti);
                log.warn("Blocked attempt using revoked token [jti={}]. Reason: {}", jti, reason);
                writeUnauthorized(response, "Token has been revoked.");
                return;
            }
        } catch (Exception e) {
            // Redis is unavailable — fail closed: deny access rather than skip the check.
            log.error("Revocation check failed (Redis unavailable?) for jti={}: {}", jti, e.getMessage());
            writeUnauthorized(response, "Authentication service temporarily unavailable.");
            return;
        }

        // ── Step 4: Authenticate — only if not already done ───────────────────────
        // SecurityContextHolder stores the authenticated principal for this thread.
        // Skipping when already populated avoids clobbering auth set by an earlier filter.
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Load the live Guest from the DB. This confirms the account still exists
            // and re-derives current roles — a role demoted in the DB takes effect here
            // even if the token was issued when the guest was an admin.
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            // ── Step 5: Final token validation ────────────────────────────────────
            // Confirms the token subject matches the loaded user and that it has not
            // expired. Both conditions must hold. (Blacklist was already checked above.)
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                // Attaches IP, session ID, and other request metadata for audit logging.
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // ── Step 6: Continue the filter chain ─────────────────────────────────────
        // Authorization rules in SecurityConfig decide whether an unauthenticated or
        // insufficiently-privileged request is rejected as 401 or 403.
        filterChain.doFilter(request, response);
    }

    /**
     * Writes a minimal {@code 401 Unauthorized} JSON error body directly to the
     * response and terminates filter processing.
     *
     * <p>This is used instead of continuing the chain because a revoked or
     * unparseable token is a hard security failure that must not reach controllers.
     * The response format mirrors the structure produced by
     * {@code GlobalExceptionHandler} for consistency across the API's error contract.
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
            {"status":401,"error":"Unauthorized","message":"%s"}""".formatted(message));
    }
}
