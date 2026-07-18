package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for {@code POST /api/payments}, deserialized from the JSON body: the
 * guest settles the balance due on a {@code PENDING} reservation to confirm the
 * hold. It is the wire contract for recording a payment; the {@code Payment} JPA
 * entity is never bound directly.
 *
 * <p>The paying guest is resolved from the JWT, not this body. The provider is
 * fixed server-side to the simulated/front-desk gateway, so it is intentionally
 * not accepted here; the client only chooses <em>how</em> they are paying via
 * {@code method}.
 *
 * <p><strong>Validation intent.</strong> Violations are reported as
 * {@code 400 Bad Request} via {@code MethodArgumentNotValidException}.
 *
 * @param reservationId the reservation being paid toward; {@code @NotNull}. The
 *        caller's ownership of it is verified in the service, not by validation.
 * @param amount the monetary amount tendered; {@code @NotNull} with
 *        {@code @DecimalMin("0.01")} — a payment must be strictly positive, so
 *        zero or negative amounts are rejected.
 * @param method the payment instrument (e.g. {@code CASH}, {@code VISA},
 *        {@code MASTERCARD}); {@code @NotBlank}.
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
