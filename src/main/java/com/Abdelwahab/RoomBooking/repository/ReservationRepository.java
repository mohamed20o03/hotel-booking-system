package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.ReservationStatus;

/**
 * Data-access layer for the {@link Reservation} aggregate — the booking that ties a
 * {@link com.Abdelwahab.RoomBooking.model.Guest} to a stay, drives the inventory
 * hold, and carries the reservation state machine
 * ({@code PENDING → CONFIRMED → CHECKED_IN}, or {@code CANCELLED}/{@code EXPIRED}).
 *
 * <p><strong>Aggregate role.</strong> The reservation is the central transactional
 * entity of the domain. The finders here support three distinct access paths: guest
 * self-service lookup by confirmation number, a guest's own booking history, and the
 * scheduled expiry sweep that reclaims lapsed holds.
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Resolves a reservation by its human-facing confirmation number, the code a
     * guest quotes to manage a booking. Backs the confirmation-number lookup endpoint.
     *
     * <p>Relies on the {@code unique} constraint on
     * {@code reservation.confirmation_number}: at most one row can match, which is why
     * the result is an {@link Optional} rather than a list.
     *
     * @param confirmationNumber the opaque confirmation code issued at booking time;
     *        matched exactly.
     * @return the matching reservation, or {@link Optional#empty()} if no reservation
     *         carries that confirmation number.
     */
    Optional<Reservation> findByConfirmationNumber(String confirmationNumber);

    /**
     * Returns every reservation belonging to a guest, most recent stay first,
     * navigating the {@code Reservation → Guest} association by guest identifier and
     * ordering by {@code checkInDate} descending. Backs the "my reservations" listing.
     *
     * @param guestId the identifier of the owning
     *        {@link com.Abdelwahab.RoomBooking.model.Guest}.
     * @return the guest's reservations ordered newest check-in first; an empty list if
     *         the guest has never booked. Never {@code null}.
     */
    List<Reservation> findByGuestIdOrderByCheckInDateDesc(Long guestId);

    /**
     * Finds holds whose payment window has already lapsed, feeding the scheduled
     * expiry sweep that releases their inventory. Scoped by both {@code status} and a
     * {@code holdExpiresAt} cutoff so that only unpaid holds are ever selected —
     * confirmed, checked-in, and cancelled reservations are structurally excluded.
     *
     * <p>Callers pass {@link ReservationStatus#PENDING} as the status; the method is
     * kept parameterised rather than hard-coding the state so the sweep's intent is
     * explicit at the call site.
     *
     * @param status the reservation status to sweep, expected to be
     *        {@code PENDING}.
     * @param cutoff the moment against which {@code holdExpiresAt} is compared;
     *        reservations expiring strictly before this instant are returned.
     * @return the expired holds matching both predicates; an empty list if none have
     *         lapsed. Never {@code null}.
     */
    List<Reservation> findByStatusAndHoldExpiresAtBefore(ReservationStatus status, LocalDateTime cutoff);
}