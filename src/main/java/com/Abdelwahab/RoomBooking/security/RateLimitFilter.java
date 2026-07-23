package com.Abdelwahab.RoomBooking.security;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.Abdelwahab.RoomBooking.exception.ErrorResponse;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Throttles the public auth endpoints per client IP, rejecting excess attempts
 * with {@code 429 Too Many Requests} before they reach authentication.
 *
 * <p>Applies <strong>only</strong> to {@code POST /api/auth/login} and
 * {@code POST /api/auth/register} — every other request passes straight through.
 * The counting and fail-open policy live in {@link RateLimitService}; this filter
 * only decides scope and shapes the rejection response.
 *
 * <p>Placed in the Spring Security chain by {@code SecurityConfig} ahead of
 * {@code UsernamePasswordAuthenticationFilter}, so a rejected request never
 * reaches the credential check. Registered as a plain {@code @Component} (no
 * {@code @Order}); ordering is defined by its position in the chain, and
 * {@code SecurityConfig} disables its stand-alone servlet registration so it
 * runs exactly once.
 *
 * @see RateLimitService
 * @see SecurityConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!isRateLimited(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        if (rateLimitService.tryAcquire(ip)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Over the limit — never log the body/credentials, only who and where.
        log.warn("Rate limit exceeded on {} {} from ip={}",
                request.getMethod(), request.getRequestURI(), ip);
        writeTooManyRequests(request, response);
    }

    /** Only the two credential-accepting POST endpoints are throttled. */
    private boolean isRateLimited(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/auth/login".equals(path) || "/api/auth/register".equals(path);
    }

    /**
     * Best-effort client IP. Honours the first hop of {@code X-Forwarded-For} when
     * present, else the socket address.
     *
     * <p>Note: {@code X-Forwarded-For} is client-spoofable unless a trusted proxy
     * that rewrites it sits in front of the app. Acceptable for this per-IP abuse
     * cap; documented as debt rather than a hard identity signal.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Too many attempts. Please try again later.",
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(rateLimitService.getWindowSeconds()));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
