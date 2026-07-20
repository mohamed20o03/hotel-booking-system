package com.Abdelwahab.RoomBooking.security;

import java.io.IOException;
import java.util.Arrays;

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

/**
 * JWT authentication filter — the per-request security gatekeeper.
 *
 * <p>As a {@link OncePerRequestFilter}, this runs exactly once per request and sits
 * ahead of all controllers. It reads the JWT from the HttpOnly {@code jwt} cookie
 * (deliberately <em>not</em> the {@code Authorization} header, so the token is
 * inaccessible to JavaScript and immune to XSS theft), validates it, and — when
 * valid — populates the {@code SecurityContext} with the authenticated principal.
 *
 * <p>If no valid token is present, the filter does not reject the request itself;
 * it passes through unauthenticated and lets the authorization rules in
 * {@link SecurityConfig} decide the outcome. An unauthenticated request to a
 * protected endpoint is then rejected by {@code RestAuthenticationEntryPoint} as
 * {@code 401 UNAUTHORIZED}, while an authenticated caller lacking the required role
 * is rejected as {@code 403 FORBIDDEN}.
 *
 * @see JwtService
 * @see SecurityConfig
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    /**
     * UserDetailsService is the bridge between Spring Security and your database.
     * When provided an email (username), it loads the full Guest entity from the DB
     * so Spring Security can verify roles, authorities, and account status.
     */
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws IOException, ServletException {

        final String jwt;
        final String userEmail;

        // ── Step 1: Extract the JWT from the HttpOnly cookie ──────────────────────
        // We read from a cookie, NOT the Authorization header.
        // HttpOnly cookies cannot be accessed by JavaScript at all — only the browser
        // can read and send them — which makes XSS token theft impossible.
        if (request.getCookies() == null) {
            // No cookies at all — this is a public request (e.g. login, register, browse hotels)
            filterChain.doFilter(request, response);
            return;
        }

        jwt = Arrays.stream(request.getCookies())
                .filter(cookie -> "jwt".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (jwt == null) {
            // Cookies exist but none named "jwt" — treat as unauthenticated
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 2: Extract the email claim from the JWT ───────────────────────────
        // The JWT subject (set during login) is the guest's email address.
        // This extraction alone does NOT verify the token — it just reads the payload.
        userEmail = jwtService.extractUsername(jwt);

        // ── Step 3: Authenticate — but only if not already authenticated ──────────
        // SecurityContextHolder stores the current user for this request thread.
        // If authentication is already set (e.g. by a previous filter), we skip this block
        // to avoid overwriting it unnecessarily.
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Load the full Guest object from the database to check roles and account status.
            // This also confirms the user still exists (e.g. not deleted after token was issued).
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            // ── Step 4: Validate the token ────────────────────────────────────────────
            // isTokenValid checks two things:
            //   1. The email in the token matches the loaded user (not tampered with)
            //   2. The token has not expired (based on the expiration date inside the token)
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // ── Step 5: Set the Security Context ──────────────────────────────────
                // Create a fully authenticated token with:
                //   - principal: the Guest object (accessible in controllers via @AuthenticationPrincipal)
                //   - credentials: null (not needed after authentication)
                //   - authorities: the guest's roles (e.g. ROLE_USER)
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                // Attach the HTTP request details (IP address, session info) to the auth token.
                // Useful for audit logging and Spring Security events.
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Store the authenticated user in the SecurityContext.
                // From this point, SecurityContextHolder.getContext().getAuthentication()
                // will return this user in any service or controller that handles this request.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // ── Step 6: Continue the filter chain ─────────────────────────────────────
        // Whether authenticated or not, pass the request to the next filter/controller.
        // If unauthenticated and the endpoint requires auth, Spring Security rejects it
        // via RestAuthenticationEntryPoint as 401 Unauthorized (an authenticated caller
        // lacking the role is rejected as 403 Forbidden instead).
        filterChain.doFilter(request, response);
    }
}
