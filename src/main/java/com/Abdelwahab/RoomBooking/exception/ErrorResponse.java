package com.Abdelwahab.RoomBooking.exception;

import java.time.LocalDateTime;

/**
 * The standard JSON error body returned by {@link GlobalExceptionHandler} for
 * every failed request. A single, consistent shape lets clients parse errors
 * uniformly regardless of which exception was raised.
 *
 * @param timestamp the server-side instant at which the error response was built
 * @param status    the numeric HTTP status code (e.g. {@code 404}, {@code 409})
 * @param error     the HTTP reason phrase for the status (e.g. {@code "Not Found"})
 * @param message   a human-readable description of the failure
 * @param path      the request URI that produced the error
 *
 * @see GlobalExceptionHandler
 */
public record ErrorResponse (
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path
) {}
