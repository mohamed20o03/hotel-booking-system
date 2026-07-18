package com.Abdelwahab.RoomBooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.RatePlan;

/**
 * Data-access layer for the {@link RatePlan} aggregate — a named pricing scheme
 * (for example "Best Flexible" or "Advance Purchase") attached to a room type and
 * carrying a base nightly rate.
 *
 * <p><strong>Aggregate role.</strong> The rate plan is the pricing entry point for a
 * {@link com.Abdelwahab.RoomBooking.model.RoomType}. A booking selects a plan, and
 * the per-night charge is resolved by the pricing engine from the plan's per-date
 * {@link com.Abdelwahab.RoomBooking.model.RatePlanRate} overrides with the plan's
 * base rate as fallback.
 *
 * @see com.Abdelwahab.RoomBooking.model.RatePlanRate
 */
@Repository
public interface RatePlanRepository extends JpaRepository<RatePlan, Long> {

    /**
     * Lists the rate plans offered for a room type, navigating the {@code RatePlan →
     * RoomType} association by room-type identifier.
     *
     * <p>The result is deliberately not filtered by any date window: a plan's base
     * rate is treated as covering every night, and per-night pricing is decided later
     * from {@link com.Abdelwahab.RoomBooking.model.RatePlanRate} overrides layered
     * over that base fallback. This finder therefore returns the full set of plans a
     * guest may choose from, leaving night-level pricing to the pricing engine.
     *
     * @param roomTypeId the identifier of the owning
     *        {@link com.Abdelwahab.RoomBooking.model.RoomType}.
     * @return the room type's rate plans; an empty list if none are configured.
     *         Never {@code null}.
     */
    List<RatePlan> findByRoomTypeId(Long roomTypeId);
}

