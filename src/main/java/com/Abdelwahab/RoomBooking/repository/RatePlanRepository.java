package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.RatePlan;

@Repository
public interface RatePlanRepository extends JpaRepository<RatePlan, Long> {

    @Query("SELECT rp FROM RatePlan rp WHERE rp.roomType.id = :roomTypeId " +
           "AND rp.validFrom < :checkOutDate " +
           "AND rp.validTo >= :checkInDate " +
           "ORDER BY rp.validFrom ASC")
    List<RatePlan> findOverlappingRatePlans(
        @Param("roomTypeId") Long roomTypeId, 
        @Param("checkInDate") LocalDate checkInDate,
        @Param("checkOutDate") LocalDate checkOutDate
    );
}
