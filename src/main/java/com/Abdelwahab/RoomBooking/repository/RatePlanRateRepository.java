package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.RatePlanRate;

/**
 * Data-access layer for the {@link RatePlanRate} aggregate — a per-date price
 * override on a {@link com.Abdelwahab.RoomBooking.model.RatePlan}, letting a hotel
 * set a specific nightly rate for individual calendar dates.
 *
 * <p><strong>Aggregate role.</strong> These rows are the fine-grained pricing layer
 * consumed by the pricing engine: for each night of a stay the engine prefers the
 * matching override and falls back to the plan's base rate when none exists. This
 * repository supplies the overrides that intersect a requested stay.
 *
 * @see com.Abdelwahab.RoomBooking.model.RatePlan
 */
@Repository
public interface RatePlanRateRepository extends JpaRepository<RatePlanRate, Long> {

    /**
     * Returns the per-date price overrides for a rate plan that fall inside the stay,
     * so the pricing engine can compute the nightly charge for each occupied night in
     * a single round trip rather than querying date by date.
     *
     * <p><strong>Hand-written rationale.</strong> A derived query cannot express the
     * half-open range predicate {@code date >= checkIn AND date < checkOut}; the
     * explicit {@link Query} makes the interval and the association navigation
     * ({@code RatePlanRate → RatePlan} by id) precise. The checkout date is
     * <em>excluded</em> because the guest is not charged for the night of departure,
     * matching the half-open stay convention used throughout the domain. Any nights
     * with no override row are simply absent from the result and are priced from the
     * plan's base rate by the caller.
     *
     * @param ratePlanId the identifier of the owning {@link RatePlan}.
     * @param checkIn the first night of the stay, inclusive.
     * @param checkOut the departure date, exclusive.
     * @return the override rows within {@code [checkIn, checkOut)}; an empty list if
     *         the plan has no per-date overrides in that range. Never {@code null}.
     */
    @Query("SELECT r FROM RatePlanRate r WHERE r.ratePlan.id = :ratePlanId " +
           "AND r.date >= :checkIn AND r.date < :checkOut")
    List<RatePlanRate> findForStay(
        @Param("ratePlanId") Long ratePlanId,
        @Param("checkIn") LocalDate checkIn,
        @Param("checkOut") LocalDate checkOut
    );
}
