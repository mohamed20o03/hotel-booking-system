package com.Abdelwahab.RoomBooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.ReservationAddon;

@Repository
public interface ReservationAddonRepository extends JpaRepository<ReservationAddon, Long> {
    
}
