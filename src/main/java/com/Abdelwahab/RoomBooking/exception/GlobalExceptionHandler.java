package com.Abdelwahab.RoomBooking.exception;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralized, application-wide exception translation for the REST API.
 *
 * <p><strong>Architectural role.</strong> As a {@link RestControllerAdvice}, this
 * class intercepts exceptions thrown from any controller and maps them to HTTP
 * status codes and a uniform {@link ErrorResponse} body. It is the single source
 * of truth for the API's error contract, keeping controllers and services free of
 * response-shaping concerns.
 *
 * <p><strong>Status mapping.</strong>
 * <ul>
 *   <li>{@link ResourceNotFoundException} &rarr; {@code 404 NOT FOUND}</li>
 *   <li>{@link NoAvailabilityException}, {@link DuplicateResourceException},
 *       {@link PaymentException} &rarr; {@code 409 CONFLICT}</li>
 *   <li>{@link org.springframework.security.core.AuthenticationException} (e.g. bad
 *       login credentials) &rarr; {@code 401 UNAUTHORIZED}</li>
 *   <li>{@link org.springframework.security.access.AccessDeniedException} &rarr;
 *       {@code 403 FORBIDDEN}</li>
 *   <li>{@link org.springframework.web.bind.MethodArgumentNotValidException},
 *       {@link IllegalArgumentException}, and
 *       {@link java.time.format.DateTimeParseException} &rarr; {@code 400 BAD REQUEST}</li>
 *   <li>any other {@link Exception} &rarr; {@code 500 INTERNAL SERVER ERROR}</li>
 * </ul>
 *
 * <p><strong>Authentication behaviour.</strong> Unauthenticated requests to protected
 * endpoints are rejected as {@code 401 UNAUTHORIZED} by
 * {@code RestAuthenticationEntryPoint} in the security chain; a failed
 * <em>credential check</em> during login raises an {@code AuthenticationException},
 * which the handler below likewise maps to {@code 401}. These are distinct from
 * {@code 403 FORBIDDEN}, which is reserved for authenticated callers who lack the
 * required role.
 *
 * @see ErrorResponse
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@link ResourceNotFoundException}, raised when a hotel, room type,
     * reservation, or guest cannot be found.
     *
     * @return {@code 404 NOT FOUND} with an {@link ErrorResponse} carrying the
     *         exception message
     */
    // 1. Handle Not Found (404)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
        ResourceNotFoundException ex, HttpServletRequest request) {

        log.debug("Resource not found on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles the family of business-conflict exceptions:
     * {@link NoAvailabilityException}, {@link DuplicateResourceException}, and
     * {@link PaymentException}. Each represents a request that clashes with the
     * current state of the domain (no inventory, a uniqueness violation, or an
     * unpayable reservation).
     *
     * @return {@code 409 CONFLICT} with an {@link ErrorResponse} carrying the
     *         exception message
     */
    // 2. Handle Business Conflicts / Duplicates (409)
    @ExceptionHandler({NoAvailabilityException.class, DuplicateResourceException.class, PaymentException.class})
    public ResponseEntity<ErrorResponse> handleConflictExceptions(
        RuntimeException ex, HttpServletRequest request) {

        // Recoverable business-state clash (no inventory, duplicate, unpayable) —
        // WARN, not ERROR: expected under contention, but worth surfacing. No stack
        // trace; the message and request path are enough to see the pattern.
        log.warn("Business conflict [{}] on {} {}: {}",
                ex.getClass().getSimpleName(), request.getMethod(), request.getRequestURI(), ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handles {@link AuthenticationException}, raised when a credential check fails —
     * most notably {@code BadCredentialsException} during login. Distinct from the
     * unauthenticated-access path, which is handled earlier in the security chain by
     * {@code RestAuthenticationEntryPoint}; both converge on the same {@code 401}
     * contract.
     *
     * @return {@code 401 UNAUTHORIZED} with a generic {@link ErrorResponse} that does
     *         not disclose whether the email or the password was at fault
     */
    // 2a. Handle Authentication Failures (401)
    // Thrown by AuthenticationManager.authenticate(...) on bad login credentials.
    // The message is deliberately generic to avoid user-enumeration.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
        AuthenticationException ex, HttpServletRequest request) {

        // Security-relevant: repeated failures on one requestId/IP hint at a
        // brute-force attempt. WARN, but never log the submitted email/password —
        // that would put a credential (and enumeration data) into the log store.
        log.warn("Authentication failed on {} {}", request.getMethod(), request.getRequestURI());

        ErrorResponse errorResponse = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            "Invalid email or password.",
            request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handles {@link AccessDeniedException}, raised by {@code @PreAuthorize} method
     * security and URL authorization rules when an authenticated caller lacks the
     * required role.
     *
     * @return {@code 403 FORBIDDEN} with a generic permission-denied
     *         {@link ErrorResponse}
     */
    // 2b. Handle Authorization Failures (403)
    // Thrown by @PreAuthorize / URL rules when an authenticated user lacks the role.
    // Must be re-mapped explicitly, otherwise the catch-all below would turn it into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
        AccessDeniedException ex, HttpServletRequest request) {

        // Authenticated caller reaching for something above their role — possible
        // privilege probing. WARN; the MDC userId already identifies who.
        log.warn("Access denied on {} {}", request.getMethod(), request.getRequestURI());

        ErrorResponse errorResponse = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            "You do not have permission to perform this action.",
            request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Handles {@link MethodArgumentNotValidException}, raised when a
     * {@code @Valid} request body fails bean-validation constraints. All field-level
     * messages are concatenated into a single, comma-separated description.
     *
     * @return {@code 400 BAD REQUEST} with an {@link ErrorResponse} listing every
     *         validation failure
     */
    // 3. Handle Validation Errors from @Valid (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
            
        // Extract all validation messages and join them into a single string
        String errorMessage = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                errorMessage,
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles {@link IllegalArgumentException}, raised for invalid-state or
     * malformed-request conditions that are not bean-validation failures — for
     * example, a check-out date that precedes the check-in date, or an operation
     * attempted against a reservation in the wrong state.
     *
     * @return {@code 400 BAD REQUEST} with an {@link ErrorResponse} carrying the
     *         exception message
     */
    // Handle generic bad requests (e.g., check-out date before check-in date)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.debug("Bad request on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles {@link DateTimeParseException}, raised when a request supplies a
     * malformed date — for example a non-ISO {@code checkIn}/{@code checkOut} query
     * parameter on the availability search. Treated as a client error rather than an
     * internal fault. ({@code DateTimeParseException} extends {@code RuntimeException},
     * not {@code IllegalArgumentException}, so it needs its own handler.)
     *
     * @return {@code 400 BAD REQUEST} with an {@link ErrorResponse} describing the
     *         malformed value
     */
    // Handle malformed date inputs (e.g., non-ISO checkIn/checkOut query params).
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ErrorResponse> handleDateTimeParseException(
            DateTimeParseException ex, HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Invalid date format: '" + ex.getParsedString() + "'. Expected ISO-8601 (yyyy-MM-dd).",
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Catch-all for any exception not matched by a more specific handler above,
     * guarding against leaking stack traces or internal detail to clients.
     *
     * @return {@code 500 INTERNAL SERVER ERROR} with a generic {@link ErrorResponse}
     */
    // 4. Catch-All for Unexpected Server Errors (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, HttpServletRequest request) {

        // The only place an unhandled failure is recorded — log at ERROR with the
        // full stack trace and request context so the incident is investigable via
        // the MDC requestId/traceId already on the thread.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred: " + ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
