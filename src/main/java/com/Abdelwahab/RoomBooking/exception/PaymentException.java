package com.Abdelwahab.RoomBooking.exception;

/**
 * Thrown when a payment cannot be applied to a reservation for a business reason
 * — e.g. the reservation is not in a payable state, or its hold has already
 * expired. Maps to 409 CONFLICT.
 */
public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }
}
