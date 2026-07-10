package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.RatePlanRate;

@Repository
public interface RatePlanRateRepository extends JpaRepository<RatePlanRate, Long> {

    /**
     * All price overrides for a rate plan that fall inside the half-open stay
     * interval [checkIn, checkOut). The checkout date is excluded because the
     * guest is not charged for the night they leave.
     */
    @Query("SELECT r FROM RatePlanRate r WHERE r.ratePlan.id = :ratePlanId " +
           "AND r.date >= :checkIn AND r.date < :checkOut")
    List<RatePlanRate> findForStay(
        @Param("ratePlanId") Long ratePlanId,
        @Param("checkIn") LocalDate checkIn,
        @Param("checkOut") LocalDate checkOut
    );
}
