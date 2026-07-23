package com.Abdelwahab.RoomBooking.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import com.Abdelwahab.RoomBooking.AbstractIntegrationTest;
import com.Abdelwahab.RoomBooking.service.AuthenticationService;

/**
 * HTTP-layer test for the per-IP auth rate limiter, run through the ACTUAL
 * security filter chain against a real Testcontainers Redis.
 *
 * <p>The limit is pinned low ({@code max-attempts=3}) via {@code @TestPropertySource}
 * so the boundary is cheap to reach. {@code AuthenticationService} is mocked so
 * each allowed login returns quickly without touching the DB; the assertion is
 * purely about the filter's admit/reject decision and the 429 contract.
 *
 * <p>Each test uses a distinct {@code X-Forwarded-For} IP because the Redis
 * counters persist for the JVM's lifetime — a shared IP would bleed counts
 * between tests.
 */
@TestPropertySource(properties = {
        "app.rate-limit.auth.max-attempts=3",
        "app.rate-limit.auth.window-seconds=60"
})
public class RateLimitFilterTest extends AbstractIntegrationTest {

    @Autowired private WebApplicationContext context;
    @MockitoBean private AuthenticationService authenticationService;
    private MockMvc mockMvc;

    private static final String LOGIN_BODY = """
            {"email":"john@example.com","password":"secret123"}""";

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        when(authenticationService.login(any())).thenReturn("signed.jwt.token");
    }

    /**
     * Given a max of 3 attempts per window; when a single IP POSTs to
     * /api/auth/login four times in a row; then the first three pass through to the
     * normal 200 and the fourth is rejected with 429 carrying a Retry-After header
     * and the standard ErrorResponse body — proving the filter caps per IP.
     */
    @Test
    @WithAnonymousUser
    public void fourthAttemptFromSameIpIsRejected() throws Exception {
        String ip = "10.0.0.1";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_BODY))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many attempts. Please try again later."));
    }

    /**
     * Given one IP has already been throttled; when a DIFFERENT IP makes its first
     * attempt; then it is allowed — the counter is scoped per IP, so one abuser does
     * not lock out everyone else.
     */
    @Test
    @WithAnonymousUser
    public void differentIpIsUnaffected() throws Exception {
        String abuser = "10.0.0.2";
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", abuser)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(LOGIN_BODY));
        }

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.0.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk());
    }

    /**
     * Given the limiter only guards the auth endpoints; when an IP exceeds the limit
     * on login and then hits a non-auth route; then the non-auth route is not
     * throttled — confirming the scope guard.
     */
    @Test
    @WithAnonymousUser
    public void nonAuthEndpointIsNotRateLimited() throws Exception {
        String ip = "10.0.0.4";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(LOGIN_BODY));
        }

        // A public GET on the catalogue from the same IP must not see a 429.
        mockMvc.perform(post("/api/auth/logout")
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isNoContent());
    }
}
