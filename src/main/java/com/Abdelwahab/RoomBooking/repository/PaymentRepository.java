package com.Abdelwahab.RoomBooking.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Payment;

/**
 * Data-access layer for the {@link Payment} aggregate — an individual payment attempt
 * recorded against a reservation, each carrying an amount and an outcome status.
 *
 * <p><strong>Aggregate role.</strong> A reservation may accumulate several payment
 * rows over its lifetime (initial charge, retries, partial payments). This repository
 * both lists those rows and computes the settled total, which the reservation
 * lifecycle uses to decide whether a booking is fully paid and to derive the balance
 * due at checkout.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Returns all payment rows recorded against a reservation regardless of outcome,
     * navigating the {@code Payment → Reservation} association by reservation
     * identifier. Backs the payment history view for a booking.
     *
     * @param reservationId the identifier of the owning
     *        {@link com.Abdelwahab.RoomBooking.model.Reservation}.
     * @return the reservation's payments in any status; an empty list if none have
     *         been recorded. Never {@code null}.
     */
    List<Payment> findByReservationId(Long reservationId);

    /**
     * Computes the total value of a reservation's <em>successful</em> payments, used to
     * establish the amount settled and the balance still due at checkout.
     *
     * <p><strong>Hand-written rationale.</strong> This is a JPQL aggregate, not a
     * derived query: it pushes the {@code SUM} down to the database so the total is
     * computed in one statement rather than loading every {@link Payment} row and
     * summing in the application. Key characteristics:
     * <ul>
     *   <li>Only rows with {@code status = 'SUCCESS'} contribute, so pending or failed
     *       attempts never inflate the settled amount.</li>
     *   <li>{@code COALESCE(SUM(...), 0)} converts the {@code NULL} that {@code SUM}
     *       returns over an empty set into a zero total, so the method never returns
     *       {@code null}.</li>
     *   <li>The result is a {@link BigDecimal} to keep money in exact decimal
     *       arithmetic — monetary values must never round-trip through {@code double}.</li>
     * </ul>
     *
     * @param reservationId the identifier of the owning
     *        {@link com.Abdelwahab.RoomBooking.model.Reservation}.
     * @return the summed amount of successful payments, or {@link BigDecimal#ZERO}
     *         (as {@code 0}) when the reservation has no successful payments. Never
     *         {@code null}.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.reservation.id = :reservationId AND p.status = 'SUCCESS'")
    BigDecimal sumSuccessfulPaymentsByReservationId(@Param("reservationId") Long reservationId);
}
