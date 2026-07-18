package com.Abdelwahab.RoomBooking.exception;

/**
 * Signals an attempt to create a resource that would violate a uniqueness
 * constraint — for example, registering a guest with an email address that is
 * already in use.
 *
 * <p>Mapped by {@link GlobalExceptionHandler} to {@code 409 CONFLICT}, indicating
 * that the request conflicts with the current state of the target resource.
 *
 * @see GlobalExceptionHandler
 */
public class DuplicateResourceException extends RuntimeException{
    public DuplicateResourceException(String message) {
        super(message);
    }
}
