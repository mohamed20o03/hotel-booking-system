package com.Abdelwahab.RoomBooking.exception;

/**
 * Signals that a requested domain resource — a hotel, room type, reservation,
 * or guest — could not be located by its identifier.
 *
 * <p>Mapped by {@link GlobalExceptionHandler} to {@code 404 NOT FOUND}, indicating
 * that the target resource does not exist.
 *
 * @see GlobalExceptionHandler
 */
public class ResourceNotFoundException extends RuntimeException{
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
