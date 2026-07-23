package com.Abdelwahab.RoomBooking.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Populates the SLF4J {@link MDC} with per-request tracing keys so every log
 * line written anywhere on the request thread automatically carries them.
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li>{@code requestId} — a fresh id minted for every HTTP request. Echoed back
 *       in the {@code X-Request-Id} response header so a client-reported failure
 *       can be matched to its exact server-side log lines.</li>
 *   <li>{@code traceId} — the cross-service correlation id. If an upstream proxy
 *       or gateway sent {@code X-Trace-Id}, it is propagated; otherwise the
 *       request id doubles as the trace id.</li>
 *   <li>{@code userId} — NOT set here. The user is unknown until the JWT is
 *       parsed, so {@code JwtAuthenticationFilter} adds it once authentication
 *       succeeds. Declaring that contract here keeps all MDC keys documented in
 *       one place.</li>
 * </ul>
 *
 * <h2>Ordering and cleanup</h2>
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE}, i.e. before the Spring Security
 * chain, so even a request rejected by the JWT filter logs with a request id.
 * The {@code finally} block clears the MDC unconditionally — servlet threads are
 * pooled and reused, and a stale {@code userId} leaking into the next request's
 * logs would corrupt any investigation built on them.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    public static final String TRACE_ID = "traceId";
    /** Set by JwtAuthenticationFilter after the token is verified. */
    public static final String USER_ID = "userId";

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Short id: the first UUID block is plenty for log correlation.
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        // Honour an inbound trace id from a gateway; fall back to our own.
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = requestId;
        }

        MDC.put(REQUEST_ID, requestId);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled — never let this request's ids leak into the next.
            MDC.clear();
        }
    }
}
