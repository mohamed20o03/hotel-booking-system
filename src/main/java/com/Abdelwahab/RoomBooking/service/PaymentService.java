package com.Abdelwahab.RoomBooking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.PaymentRequestDTO;
import com.Abdelwahab.RoomBooking.dto.PaymentResponseDTO;
import com.Abdelwahab.RoomBooking.exception.PaymentException;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Payment;
import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.ReservationStatus;
import com.Abdelwahab.RoomBooking.repository.PaymentRepository;
import com.Abdelwahab.RoomBooking.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Records payments against a reservation and confirms the booking once its balance
 * is fully settled.
 *
 * <p><strong>Role in the reservation state machine.</strong> This service drives the
 * {@code PENDING → CONFIRMED} transition. Confirmation is decided by the
 * successful-payment total, not by a single payment: partial payments (e.g. a
 * deposit) leave the reservation {@code PENDING} with a balance due; the payment
 * that brings the cumulative total up to {@code totalPrice} flips the status to
 * {@code CONFIRMED} and clears the hold's expiry, so it is no longer a timed hold.
 *
 * <p><strong>Simulated provider.</strong> This is a SIMULATED / front-desk provider:
 * there is no external gateway call, so every payment is recorded as {@code SUCCESS}
 * immediately with a generated transaction reference. Swapping in a real gateway
 * later means calling it here and setting the status/reference from its response —
 * the confirmation logic around it stays the same.
 *
 * <p><strong>Concurrency — racing the expiry sweep.</strong> {@link HoldExpirySweeper}
 * may try to expire the very same hold this service is confirming. The reservation's
 * {@code @Version} optimistic lock arbitrates: whichever transaction commits second
 * fails its version check. In practice the payment wins — either the sweep's
 * {@code expireHold} re-reads the row as no longer {@code PENDING}, or it fails with
 * an optimistic-lock exception that the sweeper swallows for that hold.
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton holding only its
 * injected repositories; correctness under concurrency comes from the version guard,
 * not instance state.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String PROVIDER = "FRONT_DESK";

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;

    /**
     * Applies a payment to a reservation on behalf of the authenticated guest and
     * confirms the booking if it becomes fully paid.
     *
     * <p>Enforces, in order: ownership (only the guest who booked may pay), a
     * {@code PENDING} status (a {@code CONFIRMED}/{@code CHECKED_IN}/etc. booking is
     * already settled, and a {@code CANCELLED}/{@code EXPIRED} one no longer holds
     * inventory), a still-live hold window (an expired-but-not-yet-swept hold is
     * rejected so a guest cannot pay for a room the sweep is about to release), and
     * an amount not exceeding the outstanding balance. On success it records a
     * {@code SUCCESS} payment and, once the cumulative total clears the balance,
     * confirms the reservation and nulls its expiry.
     *
     * <p>Read-write transactional. The reservation's {@code @Version} guards the
     * confirmation flip against a concurrent expiry sweep; if the sweep wins, this
     * transaction fails its version check and rolls back — the payment is not
     * silently applied to an expired hold. Any thrown domain exception below also
     * rolls back the transaction, so no partial payment is left recorded.
     *
     * @param request the payment to apply (target reservation id, amount, method);
     *                must be non-{@code null} and valid.
     * @return a {@link PaymentResponseDTO} describing the recorded payment, the
     *         resulting reservation status, the cumulative amount paid, and the
     *         remaining balance (never negative).
     * @throws ResourceNotFoundException if no reservation exists with the given id.
     * @throws IllegalArgumentException  if the authenticated guest does not own the
     *         reservation.
     * @throws PaymentException          if the reservation is not awaiting payment,
     *         its hold window has expired, or the amount exceeds the balance due.
     */
    @Transactional
    public PaymentResponseDTO pay(PaymentRequestDTO request) {
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        Reservation reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found with ID: " + request.reservationId()));

        // Only the guest who owns the reservation may pay for it.
        if (!reservation.getGuest().getEmail().equals(currentEmail)) {
            throw new IllegalArgumentException("You do not have permission to pay for this reservation.");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new PaymentException(String.format(
                    "Reservation %s is not awaiting payment (status: %s).",
                    reservation.getConfirmationNumber(), reservation.getStatus()));
        }

        // Reject payment on a hold that has already lapsed — the sweep will (or did)
        // release it, so honouring the payment could double-sell the room.
        if (reservation.getHoldExpiresAt() != null
                && reservation.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PaymentException(String.format(
                    "The payment window for reservation %s has expired.",
                    reservation.getConfirmationNumber()));
        }

        BigDecimal alreadyPaid = paymentRepository.sumSuccessfulPaymentsByReservationId(reservation.getId());
        BigDecimal balanceDue = reservation.getTotalPrice().subtract(alreadyPaid);

        // Don't accept money beyond what is owed.
        if (request.amount().compareTo(balanceDue) > 0) {
            throw new PaymentException(String.format(
                    "Payment of %s exceeds the balance due of %s on reservation %s.",
                    request.amount(), balanceDue, reservation.getConfirmationNumber()));
        }

        // Simulated capture: no gateway round-trip, so it always succeeds.
        LocalDateTime now = LocalDateTime.now();
        Payment payment = Payment.builder()
                .reservation(reservation)
                .amount(request.amount())
                .currency(reservation.getRatePlan().getRoomType().getCurrency())
                .type("PAYMENT")
                .method(request.method())
                .provider(PROVIDER)
                .transactionReference("SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase())
                .status("SUCCESS")
                .createdAt(now)
                .updatedAt(now)
                .build();
        payment = paymentRepository.save(payment);

        // Re-total including this payment; confirm once the balance is cleared.
        BigDecimal paidNow = alreadyPaid.add(request.amount());
        BigDecimal remaining = reservation.getTotalPrice().subtract(paidNow);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservation.setHoldExpiresAt(null); // paid — no longer a timed hold
            reservationRepository.save(reservation);
        }

        return new PaymentResponseDTO(
                payment.getId(),
                reservation.getId(),
                reservation.getConfirmationNumber(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getProvider(),
                payment.getTransactionReference(),
                payment.getStatus(),
                reservation.getStatus().name(),
                paidNow,
                remaining.max(BigDecimal.ZERO),
                payment.getCreatedAt());
    }
}
