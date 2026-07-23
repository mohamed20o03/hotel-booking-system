package com.Abdelwahab.RoomBooking.controller;

import java.util.Arrays;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.BanUserRequestDTO;
import com.Abdelwahab.RoomBooking.dto.ChangePasswordRequestDTO;
import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.LoginRequestDTO;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.service.AuthenticationService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP entry point for authentication: guest registration, login, and logout.
 *
 * <h2>Architectural Role</h2>
 * <p>A thin web-contract adapter. It binds and validates credentials, delegates all
 * business logic to {@link AuthenticationService}, and translates the issued JWT into
 * an authentication cookie on the response. Cookie assembly is the only non-delegated
 * concern here. Persistence, password hashing, credential verification, token minting,
 * and token revocation all live in the service/security layers.
 *
 * <h2>Cookie Contract</h2>
 * <p>The JWT is <em>never</em> placed in the response body. On successful register/login
 * the controller sets a {@code Set-Cookie} header for a cookie named {@code "jwt"} with:
 * <ul>
 *   <li>{@code HttpOnly} — unreadable by JavaScript, neutralising XSS token theft</li>
 *   <li>{@code Secure} — sent only over HTTPS, blocking MITM interception</li>
 *   <li>{@code SameSite=Strict} — never sent on cross-site requests, blocking CSRF</li>
 *   <li>{@code Path=/} — present on every API request</li>
 *   <li>{@code Max-Age=86400} — one day, aligned with the JWT {@code exp} claim</li>
 * </ul>
 * Logout clears the cookie ({@code Max-Age=0}) and simultaneously records the token's
 * {@code jti} in the Redis blacklist so that any copy of the token already captured by
 * an attacker is instantly invalidated server-side.
 *
 * <h2>Thread Safety</h2>
 * <p>Stateless Spring singleton. Every field is an injected collaborator; each request
 * runs in isolation on its own thread with no shared mutable state.
 *
 * <h2>Security &amp; Scope</h2>
 * <p>All three endpoints are <strong>public</strong>, matched by the
 * {@code /api/auth/**} {@code permitAll} rule in {@code SecurityConfig} — a caller
 * must reach them before holding a valid token.
 *
 * <h2>Error Contract</h2>
 * <p>Domain exceptions are mapped centrally by {@code GlobalExceptionHandler}:
 * duplicate email → {@code 409}, bean-validation failure → {@code 400}, bad
 * credentials → {@code 401}.
 *
 * @see AuthenticationService
 * @see com.Abdelwahab.RoomBooking.security.TokenBlacklistService
 * @see com.Abdelwahab.RoomBooking.exception.GlobalExceptionHandler
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Registers a new guest, mints a JWT, and delivers it in the {@code "jwt"}
     * authentication cookie. The token is never returned in the body.
     *
     * <p>Public. Self-registration always creates a standard guest, never an admin.
     *
     * @param request  the new guest's profile and credentials; validated by DTO constraints
     * @param response the servlet response the {@code Set-Cookie} header is written to
     * @return {@code 201 Created} with no body
     * @throws com.Abdelwahab.RoomBooking.exception.DuplicateResourceException if the
     *         email is already registered ({@code 409})
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @Valid @RequestBody GuestRequestDTO request,
            HttpServletResponse response) {

        String token = authenticationService.register(request);
        response.addHeader(HttpHeaders.SET_COOKIE, buildJwtCookie(token).toString());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Authenticates a guest's credentials and delivers a fresh JWT in the
     * {@code "jwt"} cookie. The token is never returned in the body.
     *
     * <p>Public. The request body is validated with {@code @Valid} before the
     * service is reached.
     *
     * @param request  the login credentials — email and password
     * @param response the servlet response the {@code Set-Cookie} header is written to
     * @return {@code 200 OK} with no body
     * @throws org.springframework.security.core.AuthenticationException if credentials
     *         are invalid ({@code 401})
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletResponse response) {

        String token = authenticationService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, buildJwtCookie(token).toString());
        return ResponseEntity.ok().build();
    }

    /**
     * Logs the guest out with a two-pronged invalidation strategy:
     *
     * <ol>
     *   <li><strong>Server-side revocation.</strong> The token's {@code jti} is
     *       extracted and recorded in the Redis blacklist, so any copy captured before
     *       logout is rejected on its next use. Delegated to
     *       {@link AuthenticationService#logout}.</li>
     *   <li><strong>Client-side cookie clearance.</strong> A {@code Set-Cookie} header
     *       with {@code Max-Age=0} instructs the browser to delete the cookie
     *       immediately.</li>
     * </ol>
     *
     * <p>Public; safe to call whether or not a valid session exists.
     *
     * @param request  the incoming HTTP request, used to read the existing {@code jwt} cookie
     * @param response the servlet response the clearing {@code Set-Cookie} header is written to
     * @return {@code 204 No Content}
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = Arrays.stream(request.getCookies() != null ? request.getCookies() : new Cookie[0])
            .filter(c -> "jwt".equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);

        authenticationService.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, buildClearCookie().toString());
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Private cookie builders
    // ─────────────────────────────────────────────────────────────

    /**
     * Bans a guest globally, immediately revoking every active token across all devices.
     *
     * <p>Restricted to {@code ROLE_ADMIN} at both the URL-matcher and {@code @PreAuthorize}
     * levels. Delegates all business logic (Redis write + audit log) to
     * {@link AuthenticationService#banUser}.
     *
     * @param userId  the numeric guest ID to ban; taken from the path variable
     * @param request the ban reason; must not be blank
     * @return {@code 200 OK} with a confirmation message
     */
    @PostMapping("/admin/ban/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> banUser(
            @PathVariable Long userId,
            @Valid @RequestBody BanUserRequestDTO request) {

        log.info("Admin ban requested [targetUserId={}]", userId);
        authenticationService.banUser(userId, request.reason());
        return ResponseEntity.ok("User " + userId + " has been banned. Reason: " + request.reason());
    }

    /**
     * Allows an authenticated guest to change their own password.
     *
     * <p>Delegates all business logic (BCrypt re-verification, password write, global
     * token revocation, and audit log) to {@link AuthenticationService#changePassword}.
     * The controller's only additional responsibility is clearing the current device's
     * cookie via a {@code Set-Cookie: Max-Age=0} header.
     *
     * @param guest    the authenticated caller, injected via {@code @AuthenticationPrincipal}
     * @param request  current and new passwords
     * @param response the servlet response; the clearing {@code Set-Cookie} header is written here
     * @return {@code 204 No Content}
     */
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Guest guest,
            @Valid @RequestBody ChangePasswordRequestDTO request,
            HttpServletResponse response) {

        authenticationService.changePassword(guest.getEmail(), request);
        response.addHeader(HttpHeaders.SET_COOKIE, buildClearCookie().toString());
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Private cookie builders
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds a secure, production-ready JWT cookie.
     *
     * <p>Every attribute serves a specific security purpose:
     * <ul>
     *   <li>{@code HttpOnly} — JavaScript cannot read this cookie (XSS cannot steal the token)</li>
     *   <li>{@code Secure} — sent only over HTTPS (blocks MITM on plaintext HTTP)</li>
     *   <li>{@code SameSite=Strict} — never sent on cross-site requests (blocks CSRF)</li>
     *   <li>{@code Path=/} — sent with every API request</li>
     *   <li>{@code Max-Age=86400} — one day, aligned with the configured JWT {@code exp}</li>
     * </ul>
     */
    private ResponseCookie buildJwtCookie(String token) {
        return ResponseCookie.from("jwt", token)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(86400)
            .sameSite("Strict")
            .build();
    }

    /**
     * Builds a cookie that immediately clears the JWT from the browser.
     *
     * <p>{@code Max-Age=0} with the same name and path instructs the browser to
     * delete the existing cookie. The empty value is discarded by the browser.
     */
    private ResponseCookie buildClearCookie() {
        return ResponseCookie.from("jwt", "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(0)
            .sameSite("Strict")
            .build();
    }
}
