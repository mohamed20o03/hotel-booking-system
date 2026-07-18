package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.RoomTypeInventory;

import jakarta.persistence.LockModeType;

/**
 * Data-access layer for the {@link RoomTypeInventory} aggregate — one row per room
 * type per calendar date, recording {@code totalRooms} and {@code bookedCount} for
 * that night.
 *
 * <p><strong>Aggregate role.</strong> This is the allotment ledger that count-based
 * availability is decided against: a night is bookable while
 * {@code bookedCount < totalRooms}. The {@code room_type_inventory} table enforces a
 * {@code unique} constraint on {@code (room_type_id, date)}, so at most one ledger row
 * exists per type per night and the {@code booked_count} for a night has a single
 * authoritative value.
 *
 * <p><strong>Concurrency contract.</strong> The two finders below intentionally
 * differ in locking. {@link #findForStay} reads without a lock for availability
 * display, while {@link #lockForStay} takes a pessimistic write lock to serialise the
 * check-then-increment that prevents overselling. Choosing the wrong one for a
 * write path is a correctness bug, not merely a performance choice.
 */
@Repository
public interface RoomTypeInventoryRepository extends JpaRepository<RoomTypeInventory, Long> {

    /**
     * Reads the inventory rows for a stay {@code [checkIn, checkOut)} without any
     * lock, for availability display and search.
     *
     * <p><strong>Hand-written rationale.</strong> An explicit {@link Query} is used
     * because the half-open date range {@code date >= checkIn AND date < checkOut} is
     * not expressible as a derived finder. Because it acquires <em>no</em> lock this
     * method never blocks concurrent bookings and must not be used to gate a write:
     * the count it returns may be stale by the time a caller acts on it. The checkout
     * date is excluded, as the departing night is not occupied.
     *
     * @param roomTypeId the identifier of the {@link com.Abdelwahab.RoomBooking.model.RoomType}
     *        whose allotment is read.
     * @param checkIn the first night of the stay, inclusive.
     * @param checkOut the departure date, exclusive.
     * @return the ledger rows for each night in {@code [checkIn, checkOut)}; an empty
     *         list if no inventory rows have been provisioned for that range. Never
     *         {@code null}.
     */
    @Query("SELECT i FROM RoomTypeInventory i " +
           "WHERE i.roomType.id = :roomTypeId AND i.date >= :checkIn AND i.date < :checkOut")
    List<RoomTypeInventory> findForStay(
        @Param("roomTypeId") Long roomTypeId,
        @Param("checkIn") LocalDate checkIn,
        @Param("checkOut") LocalDate checkOut);

    /**
     * Selects and <strong>write-locks</strong> the inventory rows for a stay for the
     * remainder of the current transaction, serialising the availability
     * check-then-increment of {@code bookedCount} so concurrent bookings cannot
     * oversell the same nights.
     *
     * <p><strong>Locking contract.</strong> The {@link Lock} with
     * {@link LockModeType#PESSIMISTIC_WRITE} issues a {@code SELECT ... FOR UPDATE}:
     * each returned row is locked, and any other transaction attempting to lock the
     * same night blocks until this transaction commits or rolls back. This makes the
     * booking sequence — read the count, verify {@code bookedCount < totalRooms},
     * increment, persist — atomic against competing bookers for those nights.
     *
     * <p><strong>Transaction requirement.</strong> The lock lives only for the life of
     * the surrounding transaction, so callers <em>must</em> invoke this from within an
     * active transaction (a {@code @Transactional} service method); calling it outside
     * one would release the lock immediately and defeat its purpose.
     *
     * <p><strong>Deadlock avoidance.</strong> The {@code ORDER BY i.date} clause
     * guarantees rows are locked in a consistent date order across all bookers, so two
     * transactions competing for overlapping stays acquire the shared nights in the
     * same sequence and cannot deadlock by locking them in opposite orders.
     *
     * <p><strong>Hand-written rationale.</strong> Beyond the half-open range predicate
     * (which already rules out a derived query), the method exists to attach the
     * pessimistic lock and the deterministic ordering that the concurrency guarantee
     * depends on.
     *
     * @param roomTypeId the identifier of the {@link com.Abdelwahab.RoomBooking.model.RoomType}
     *        being booked.
     * @param checkIn the first night of the stay, inclusive.
     * @param checkOut the departure date, exclusive.
     * @return the locked ledger rows for each night in {@code [checkIn, checkOut)},
     *         ordered by date; an empty list if no inventory rows exist for the range
     *         (nothing is locked in that case). Never {@code null}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM RoomTypeInventory i " +
           "WHERE i.roomType.id = :roomTypeId AND i.date >= :checkIn AND i.date < :checkOut " +
           "ORDER BY i.date")
    List<RoomTypeInventory> lockForStay(
        @Param("roomTypeId") Long roomTypeId,
        @Param("checkIn") LocalDate checkIn,
        @Param("checkOut") LocalDate checkOut);
}
