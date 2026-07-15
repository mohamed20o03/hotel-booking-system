package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO returned after a payment is recorded. Echoes the stored payment
 * plus the reservation's resulting state so the client knows, in one round-trip,
 * whether the booking is now CONFIRMED and what balance (if any) is still due.
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
