package com.Abdelwahab.RoomBooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Addon;

/**
 * Data-access layer for the {@link Addon} aggregate — the catalogue of ancillary
 * products (breakfast, parking, spa credit, and similar extras) that a hotel offers
 * on top of a room stay.
 *
 * <p><strong>Aggregate role.</strong> An {@code Addon} is a hotel-scoped catalogue
 * entry; it is the definition a guest selects from, distinct from a
 * {@link com.Abdelwahab.RoomBooking.model.ReservationAddon} which records that a
 * particular reservation actually purchased it. This repository serves the read side
 * of that catalogue, while inherited {@link JpaRepository} operations cover
 * create/update/delete for administrative maintenance.
 *
 * @see com.Abdelwahab.RoomBooking.model.ReservationAddon
 */
@Repository
public interface AddonRepository extends JpaRepository<Addon, Long> {

    /**
     * Returns the add-ons a given hotel currently sells, filtered to those flagged
     * available. Navigates the {@code Addon → Hotel} association by hotel identifier
     * and applies the {@code available = true} predicate, so retired or temporarily
     * suspended catalogue entries are omitted and never presented for purchase.
     *
     * @param hotelId the identifier of the owning {@link com.Abdelwahab.RoomBooking.model.Hotel}.
     * @return the hotel's currently available add-ons; an empty list if the hotel
     *         offers none or has none marked available. Never {@code null}.
     */
    List<Addon> findByHotelIdAndAvailableTrue(Long hotelId);
}
