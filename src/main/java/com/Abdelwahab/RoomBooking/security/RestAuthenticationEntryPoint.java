package com.Abdelwahab.RoomBooking.security;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.Abdelwahab.RoomBooking.exception.ErrorResponse;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Entry point that answers <em>unauthenticated</em> access to a protected endpoint
 * with {@code 401 UNAUTHORIZED}.
 *
 * <p><strong>Why this exists.</strong> Without a registered
 * {@link AuthenticationEntryPoint}, Spring Security's {@code ExceptionTranslationFilter}
 * falls back to a bare {@code 403 FORBIDDEN} for anonymous callers, blurring the line
 * between "you are not logged in" and "you are logged in but lack permission." This
 * component draws that line: it is invoked <strong>only</strong> when the request has
 * no established identity, so it can safely report {@code 401}. Authenticated callers
 * who lack the required role never reach here — they are routed to the access-denied
 * handler and continue to receive {@code 403}
 * (see {@code GlobalExceptionHandler.handleAccessDeniedException}).
 *
 * <p><strong>Response contract.</strong> The body is the same {@link ErrorResponse}
 * record used everywhere else in the API, serialized with the application's configured
 * {@link ObjectMapper} so timestamps render identically to controller-advice errors.
 *
 * @see SecurityConfig
 * @see ErrorResponse
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * Writes a {@code 401 UNAUTHORIZED} {@link ErrorResponse} to the response when an
     * unauthenticated request is denied access to a protected resource.
     *
     * @param request       the request that failed authentication
     * @param response      the response to which the error body is written
     * @param authException the reason authentication was required and absent
     * @throws IOException if the response body cannot be written
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Authentication is required to access this resource.",
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
