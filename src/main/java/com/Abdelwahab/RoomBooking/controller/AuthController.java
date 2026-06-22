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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * POST /api/auth/register
     *
     * Registers a new guest, generates a JWT, and delivers it via an HttpOnly cookie.
     * The token is NEVER returned in the response body — this prevents JavaScript from
     * ever reading or stealing it (defense against XSS attacks).
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @Valid @RequestBody GuestRequestDTO request,
            HttpServletResponse response) {

        String token = authenticationService.register(request);

        // Write the JWT into the Set-Cookie header rather than the response body.
        // The browser stores this automatically and sends it with every future request.
        response.addHeader(HttpHeaders.SET_COOKIE, buildJwtCookie(token).toString());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * POST /api/auth/login
     *
     * Validates credentials via Spring Security's AuthenticationManager, generates a
     * JWT, and delivers it in an HttpOnly cookie. Returns 200 OK with no body.
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
     * POST /api/auth/logout
     *
     * Logs the guest out by overwriting the JWT cookie with an empty, immediately
     * expired cookie (Max-Age=0). The browser discards it automatically.
     * No JWT blacklist is needed — the cookie is simply gone from the browser.
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
