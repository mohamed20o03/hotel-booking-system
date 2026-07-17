package com.Abdelwahab.RoomBooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.ReservationAddon;

@Repository
public interface ReservationAddonRepository extends JpaRepository<ReservationAddon, Long> {

    // Every add-on attached to a reservation (for the reservation's add-on list).
    List<ReservationAddon> findByReservationId(Long reservationId);
}
