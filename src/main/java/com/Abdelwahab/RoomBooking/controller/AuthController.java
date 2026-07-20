package com.Abdelwahab.RoomBooking.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.LoginRequestDTO;
import com.Abdelwahab.RoomBooking.service.AuthenticationService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * HTTP entry point for authentication: guest registration, login, and logout.
 *
 * <p><strong>Architectural role.</strong> A thin web-contract adapter. It binds and
 * validates credentials, delegates to {@link AuthenticationService}, and translates
 * the issued JWT into an authentication cookie on the response. It holds no business
 * logic beyond cookie assembly: persistence, password hashing, credential checks, and
 * token minting all live in the service layer.
 *
 * <p><strong>Cookie contract.</strong> The JWT is never placed in the response body.
 * On successful register/login the controller sets a {@code Set-Cookie} header for a
 * cookie named {@code "jwt"} that is {@code HttpOnly} (unreadable by JavaScript,
 * neutralising token theft via XSS), {@code Secure} (sent only over HTTPS),
 * {@code Path=/} (sent with every API request), {@code Max-Age=86400} (one day, to
 * match the configured token expiry), and {@code SameSite=Strict} (never sent on
 * cross-site requests, blocking CSRF). Logout overwrites that cookie with an empty,
 * {@code Max-Age=0} cookie of the same name and path so the browser discards it; no
 * server-side token blacklist is kept.
 *
 * <p><strong>Thread safety.</strong> Stateless and therefore thread-safe. Its only
 * field is the injected singleton {@link AuthenticationService}; each request runs on
 * its own thread with request-scoped arguments.
 *
 * <p><strong>Security &amp; scope.</strong> All three endpoints are <strong>public</strong>,
 * matched by the {@code /api/auth/**} {@code permitAll} rule in {@code SecurityConfig}
 * — a caller must be able to reach them before holding a token.
 *
 * <p><strong>Error contract.</strong> Domain exceptions are mapped centrally by
 * {@code GlobalExceptionHandler}: a duplicate email on registration raises
 * {@code DuplicateResourceException → 409}, and bean-validation failures on
 * {@code @Valid → 400}. Bad login credentials raise an {@code AuthenticationException},
 * which is mapped to {@code 401 Unauthorized} with a generic message.
 *
 * @see AuthenticationService
 * @see com.Abdelwahab.RoomBooking.exception.GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Registers a new guest, mints a JWT, and delivers it in the {@code "jwt"}
     * authentication cookie (see the class-level cookie contract). The token is never
     * returned in the body.
     *
     * <p>Public. Self-registration always creates a standard guest, never an admin.
     * The request body is validated with {@code @Valid} before the service is reached.
     *
     * @param request  the new guest's details — name, email, password, and profile
     *                fields; validated by DTO constraints.
     * @param response the servlet response the {@code Set-Cookie} header is written to.
     * @return {@code 201 Created} with no body; the JWT is delivered via the cookie.
     * @throws com.Abdelwahab.RoomBooking.exception.DuplicateResourceException if the
     *         email is already registered (mapped to {@code 409}).
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if the body
     *         fails bean validation (mapped to {@code 400}).
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
     * Authenticates a guest's credentials via Spring Security, mints a JWT, and
     * delivers it in the {@code "jwt"} authentication cookie (see the class-level
     * cookie contract). The token is never returned in the body.
     *
     * <p>Public. The request body is validated with {@code @Valid} before the service
     * is reached.
     *
     * @param request  the login credentials — email and password; validated by DTO
     *                constraints.
     * @param response the servlet response the {@code Set-Cookie} header is written to.
     * @return {@code 200 OK} with no body; the JWT is delivered via the cookie.
     * @throws org.springframework.security.core.AuthenticationException if the
     *         credentials are invalid (mapped to {@code 401}).
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if the body
     *         fails bean validation (mapped to {@code 400}).
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
     * Logs the guest out by overwriting the {@code "jwt"} cookie with an empty,
     * immediately expired ({@code Max-Age=0}) cookie so the browser discards it. No
     * server-side token blacklist is needed.
     *
     * <p>Public; safe to call whether or not a valid session exists.
     *
     * @param response the servlet response the clearing {@code Set-Cookie} header is
     *                written to.
     * @return {@code 204 No Content}.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildClearCookie().toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * Builds a secure, production-ready JWT cookie.
     *
     * Every attribute here serves a specific security purpose:
     *   - HttpOnly:       JavaScript CANNOT read this cookie. Even if an attacker injects
     *                     malicious JS into the page (XSS), they cannot steal the token.
     *   - Secure:         The browser ONLY sends this cookie over HTTPS. Prevents token
     *                     interception on plain HTTP connections (man-in-the-middle attacks).
     *   - Path("/"):      The cookie is sent with every request to the API, not just one path.
     *   - MaxAge(86400):  The cookie lives for exactly 1 day (86400 seconds), matching the
     *                     JWT expiry configured in application.yaml.
     *   - SameSite=Strict: The browser NEVER sends this cookie if the request originated from
     *                     a different website. This completely blocks CSRF attacks — a malicious
     *                     site cannot trigger an authenticated action on behalf of the user.
     */
    private ResponseCookie buildJwtCookie(String token) {
        return ResponseCookie.from("jwt", token)
            .httpOnly(true) // Browser automatically hides this cookie from all JS scripts — completely neutralizing XSS attacks
            .secure(true)
            .path("/")
            .maxAge(86400)
            .sameSite("Strict")
            .build();
    }

    /**
     * Builds a cookie that immediately clears the JWT from the browser.
     *
     * By setting Max-Age=0 with the same name and path, the browser interprets this
     * as an instruction to delete the existing cookie. The value is set to empty string
     * as the browser will discard it anyway.
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
