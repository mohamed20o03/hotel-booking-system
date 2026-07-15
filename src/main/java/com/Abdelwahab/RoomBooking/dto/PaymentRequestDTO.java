package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for POST /api/payments — the guest settles the balance due on a
 * PENDING reservation to confirm the hold.
 *
 * The paying guest is taken from the JWT, not the body. Provider is fixed to the
 * simulated/front-desk gateway server-side, so it is not accepted here either;
 * the client only chooses how they are paying (method).
 */
public record PaymentRequestDTO(

    @NotNull(message = "Reservation ID cannot be empty")
    Long reservationId,

    @NotNull(message = "Payment amount cannot be empty")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    BigDecimal amount,

    @NotBlank(message = "Payment method cannot be empty") // e.g. CASH, VISA, MASTERCARD
    String method

) {}
