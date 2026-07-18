package com.Abdelwahab.RoomBooking.exception;

/**
 * Signals that a payment cannot be applied to a reservation for a business
 * reason — for example, the reservation is not in a payable state, or its
 * inventory hold has already expired.
 *
 * <p>Mapped by {@link GlobalExceptionHandler} to {@code 409 CONFLICT}, indicating
 * that the request conflicts with the current state of the reservation.
 *
 * @see GlobalExceptionHandler
 */
public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }
}
