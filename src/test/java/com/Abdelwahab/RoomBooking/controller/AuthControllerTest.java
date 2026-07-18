package com.Abdelwahab.RoomBooking.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import com.Abdelwahab.RoomBooking.service.AuthenticationService;

/**
 * HTTP-layer test for AuthController — the PUBLIC + COOKIE-ISSUANCE pattern.
 *
 * Unique among the controllers: /api/auth/** is permitAll, and the token is
 * delivered in a Set-Cookie header rather than a JSON body. So the assertions
 * here are about the COOKIE CONTRACT the security model depends on —
 *   - login/register issue an HttpOnly, Secure, SameSite=Strict cookie named "jwt",
 *   - the token never appears in the response body,
 *   - logout clears the cookie (Max-Age=0),
 *   - a malformed credential body is rejected with 400.
 * These attributes are what §5 of docs/SECURITY.md relies on; this test is the
 * guard that stops a refactor from silently dropping HttpOnly or SameSite.
 */
@SpringBootTest
public class AuthControllerTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;
    @MockitoBean private AuthenticationService authenticationService;

    // See MaintenanceControllerTest.setUp: springSecurity() must be applied or the
    // filter chain (and its Set-Cookie handling) won't run under Boot 4.1.
    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final String LOGIN_BODY = """
            {"email":"john@example.com","password":"secret123"}""";

    // Login issues the JWT cookie with all its security attributes, and no body token.
    @Test
    @WithAnonymousUser
    public void login_setsHardenedJwtCookie() throws Exception {
        when(authenticationService.login(any())).thenReturn("signed.jwt.token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt=signed.jwt.token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
    }

    // Registration is public too and issues the same cookie, returning 201.
    @Test
    @WithAnonymousUser
    public void register_returns201_andSetsCookie() throws Exception {
        when(authenticationService.register(any())).thenReturn("signed.jwt.token");

        String body = """
                {"firstName":"John","lastName":"Doe","email":"john@example.com",
                 "password":"secret123","phone":"+201000000000","nationality":"EG",
                 "documentType":"PASSPORT","documentNumber":"A1234567","dateOfBirth":"1990-05-14"}""";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt=signed.jwt.token")));
    }

    // Logout clears the cookie: same name, empty value, Max-Age=0 so the browser drops it.
    @Test
    @WithAnonymousUser
    public void logout_clearsJwtCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    // A malformed login body (blank password) fails @Valid with 400, never reaching the service.
    @Test
    @WithAnonymousUser
    public void login_returns400_whenPasswordBlank() throws Exception {
        String badBody = """
                {"email":"john@example.com","password":""}""";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest());

        verify(authenticationService, never()).login(any());
    }
}
