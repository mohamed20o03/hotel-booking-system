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
 * Records payments against a reservation and confirms the booking once its
 * balance is fully settled.
 *
 * This is a SIMULATED / front-desk provider: there is no external gateway call,
 * so every payment is recorded as SUCCESS immediately with a generated
 * transaction reference. Swapping in a real gateway later means calling it here
 * and setting the status/reference from its response — the confirmation logic
 * around it stays the same.
 *
 * Confirmation is driven by the successful-payment total, not by a single
 * payment: partial payments (e.g. a deposit) leave the reservation PENDING with
 * a balance due; the payment that brings the total up to totalPrice flips it to
 * CONFIRMED and clears the hold's expiry. The reservation's @Version guards this
 * flip against a concurrent expiry sweep.
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
     * Rejects payment unless the reservation is PENDING (a live, unpaid hold): a
     * CONFIRMED/CHECKED_IN/etc. booking is already settled, and a CANCELLED or
     * EXPIRED one no longer holds inventory. An expired-but-not-yet-swept hold is
     * also rejected, so a guest can't pay for a room the sweep is about to release.
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
