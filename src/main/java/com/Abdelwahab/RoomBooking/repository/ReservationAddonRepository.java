package com.Abdelwahab.RoomBooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.ReservationAddon;

/**
 * Data-access layer for the {@link ReservationAddon} aggregate — a line item
 * recording that a specific reservation purchased a specific catalogue
 * {@link com.Abdelwahab.RoomBooking.model.Addon}.
 *
 * <p><strong>Aggregate role.</strong> This is the transactional counterpart to the
 * {@link com.Abdelwahab.RoomBooking.model.Addon} catalogue: where an {@code Addon}
 * defines what a hotel offers, a {@code ReservationAddon} captures a captured sale
 * against one booking and contributes to that booking's total. Rows are created and
 * removed while a reservation is still {@code PENDING}.
 *
 * @see com.Abdelwahab.RoomBooking.model.Addon
 */
@Repository
public interface ReservationAddonRepository extends JpaRepository<ReservationAddon, Long> {

    /**
     * Returns the add-on line items attached to a reservation, navigating the
     * {@code ReservationAddon → Reservation} association by reservation identifier.
     * Backs the per-reservation add-on listing and total recomputation.
     *
     * @param reservationId the identifier of the owning
     *        {@link com.Abdelwahab.RoomBooking.model.Reservation}.
     * @return the reservation's add-on lines; an empty list if none are attached.
     *         Never {@code null}.
     */
    List<ReservationAddon> findByReservationId(Long reservationId);
}
