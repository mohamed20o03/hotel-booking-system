package com.Abdelwahab.RoomBooking.exception;

/**
 * Signals that a booking cannot be fulfilled because no inventory is available
 * for the requested room type over the requested stay, or because no physical
 * room can be assigned at check-in.
 *
 * <p>Mapped by {@link GlobalExceptionHandler} to {@code 409 CONFLICT}, indicating
 * that the request conflicts with the current availability state.
 *
 * @see GlobalExceptionHandler
 */
public class NoAvailabilityException extends RuntimeException {
    public NoAvailabilityException(String message) {
        super(message);
    }
}
