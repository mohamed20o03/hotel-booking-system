package com.Abdelwahab.RoomBooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.RatePlan;

@Repository
public interface RatePlanRepository extends JpaRepository<RatePlan, Long> {

    /**
     * All rate plans offered for a room type. Availability is no longer decided
     * by a plan's date window (the base rate always covers any night); pricing
     * per night is resolved from RatePlanRate overrides + base fallback.
     */
    List<RatePlan> findByRoomTypeId(Long roomTypeId);
}

