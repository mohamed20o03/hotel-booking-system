package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO returned after a payment is recorded, serialized to the JSON body
 * of {@code POST /api/payments}. It is the outbound wire shape; the
 * {@code Payment} JPA entity is never serialized directly. It echoes the stored
 * payment together with the reservation's resulting state so the client learns,
 * in one round-trip, whether the booking is now {@code CONFIRMED} and what balance
 * (if any) is still due.
 *
 * @param paymentId the recorded payment's identifier.
 * @param reservationId the reservation the payment was applied to.
 * @param confirmationNumber the reservation's human-facing confirmation code.
 * @param amount the amount of this payment.
 * @param currency the ISO 4217 code the amounts are quoted in.
 * @param method the payment instrument used (e.g. {@code CASH}, {@code VISA}).
 * @param provider the gateway that processed it (fixed server-side).
 * @param transactionReference the gateway's reference for the transaction.
 * @param status this payment's outcome (e.g. {@code SUCCESS}).
 * @param reservationStatus the reservation's status after applying this payment
 *        (e.g. {@code CONFIRMED}).
 * @param amountPaid the running total of successful payments on the reservation.
 * @param balanceDue the outstanding balance ({@code totalPrice - amountPaid});
 *        never negative.
 * @param createdAt when the payment was recorded.
 */
public record PaymentResponseDTO(
    Long paymentId,
    Long reservationId,
    String confirmationNumber,
    BigDecimal amount,
    String currency,
    String method,
    String provider,
    String transactionReference,
    String status,              // payment status, e.g. SUCCESS
    String reservationStatus,   // reservation status after this payment, e.g. CONFIRMED
    BigDecimal amountPaid,      // total successful payments on the reservation so far
    BigDecimal balanceDue,      // totalPrice - amountPaid (never negative)
    LocalDateTime createdAt
) {}
