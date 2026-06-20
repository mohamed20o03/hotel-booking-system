package com.Abdelwahab.RoomBooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Addon;

@Repository
public interface AddonRepository extends JpaRepository<Addon, Long> {
    // Fetch all active add-ons offered by a specific hotel
    List<Addon> findByHotelIdAndAvailableTrue(Long hotelId);
}
