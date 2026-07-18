package com.Abdelwahab.RoomBooking.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.Abdelwahab.RoomBooking.exception.NoAvailabilityException;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.model.RoomTypeInventory;
import com.Abdelwahab.RoomBooking.repository.RoomTypeInventoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Owns the room-type allotment calendar ({@link RoomTypeInventory}) — the
 * count-of-rooms-per-night ledger that decides whether a stay can be sold.
 *
 * <p><strong>Model.</strong> A room type is sold by COUNT per night, not by pinning
 * a physical room at booking time. Each night carries a total capacity and a booked
 * count; the difference is what remains sellable.
 *
 * <p><strong>Concurrency — the oversell guard.</strong> The mutating methods
 * ({@link #reserve}, {@link #release}, {@link #decrementCapacity},
 * {@link #incrementCapacity}) acquire a pessimistic write lock on each night row via
 * {@code RoomTypeInventoryRepository.lockForStay} ({@code SELECT ... FOR UPDATE}).
 * This serializes competing bookings for the same last room: a rival transaction
 * blocks until this one commits, so the count can never be double-decremented and
 * the room type can never be oversold. Because those locks are held only until the
 * surrounding transaction commits, <strong>every mutating method here MUST run
 * inside the caller's booking transaction</strong> — they are not independently
 * transactional. The non-locking reads ({@link #isAvailable},
 * {@link #availableCount}) take no lock and are for display only.
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton holding only its
 * injected repository; correctness under concurrency comes from the database locks,
 * not from instance state.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final RoomTypeInventoryRepository inventoryRepository;

    /**
     * Reports whether the room type is bookable for the whole stay, without locking.
     *
     * <p>Returns {@code true} only if every night in {@code [checkIn, checkOut)} is
     * open for sale (has an inventory row) AND still has a room left. A missing row
     * means the hotel has not opened that date, which counts as unavailable. This is
     * a non-locking read intended for search; it offers no guarantee that the room
     * is still free by the time a booking transaction locks the rows.
     *
     * @param roomTypeId the room type to check.
     * @param checkIn    the first night of the stay (inclusive).
     * @param checkOut   the checkout date (exclusive; not itself a charged night);
     *                   assumed strictly after {@code checkIn}.
     * @return {@code true} if every night is open and has remaining capacity;
     *         {@code false} otherwise.
     */
    public boolean isAvailable(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        long nightsNeeded = checkIn.datesUntil(checkOut).count();
        List<RoomTypeInventory> rows = inventoryRepository.findForStay(roomTypeId, checkIn, checkOut);
        if (rows.size() < nightsNeeded) {
            return false; // at least one night is not open for sale
        }
        return rows.stream().allMatch(row -> row.remaining() > 0);
    }

    /**
     * Returns how many more reservations of this type could still be taken for the
     * exact stay, without locking.
     *
     * <p>The result is the smallest number of rooms free on any single night of the
     * stay — the bottleneck night governs. Zero if any night is closed or sold out.
     * Non-locking; intended for display in search results, so it carries no booking
     * guarantee.
     *
     * @param roomTypeId the room type to measure.
     * @param checkIn    the first night of the stay (inclusive).
     * @param checkOut   the checkout date (exclusive); assumed strictly after
     *                   {@code checkIn}.
     * @return the minimum remaining count across all nights, or {@code 0} if any
     *         night is closed or sold out.
     */
    public int availableCount(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        long nightsNeeded = checkIn.datesUntil(checkOut).count();
        List<RoomTypeInventory> rows = inventoryRepository.findForStay(roomTypeId, checkIn, checkOut);
        if (rows.size() < nightsNeeded) {
            return 0;
        }
        return rows.stream().mapToInt(RoomTypeInventory::remaining).min().orElse(0);
    }

    /**
     * Atomically holds one room of the type for every night in the stay.
     *
     * <p>Acquires a pessimistic write lock on each night row
     * ({@code SELECT ... FOR UPDATE}) so a competing booking for the same last room
     * blocks until this transaction commits — the core oversell guard. Increments
     * each night's booked count once availability is confirmed.
     *
     * <p><strong>Must be called within the caller's booking transaction</strong> so
     * the locks are held through commit.
     *
     * @param roomType the room type to hold; its id and name are read.
     * @param checkIn  the first night to hold (inclusive).
     * @param checkOut the checkout date (exclusive); assumed strictly after
     *                 {@code checkIn}.
     * @throws NoAvailabilityException if any night is closed for sale or already sold
     *         out; propagating this rolls back the entire booking transaction so no
     *         partial hold is left behind.
     */
    public void reserve(RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
        long nightsNeeded = checkIn.datesUntil(checkOut).count();
        List<RoomTypeInventory> rows = inventoryRepository.lockForStay(
                roomType.getId(), checkIn, checkOut);

        if (rows.size() < nightsNeeded) {
            throw new NoAvailabilityException(
                    "Selected dates are not open for booking for room type: " + roomType.getName());
        }

        for (RoomTypeInventory row : rows) {
            if (row.remaining() <= 0) {
                throw new NoAvailabilityException(String.format(
                        "Room type '%s' is sold out on %s.", roomType.getName(), row.getDate()));
            }
            row.setBookedCount(row.getBookedCount() + 1);
        }
        inventoryRepository.saveAll(rows);
    }

    /**
     * Returns one held room per night back to the sellable allotment.
     *
     * <p>Used when a hold is cancelled or expires. Acquires the pessimistic write
     * lock on each night row so the decrement of the booked count is atomic against
     * concurrent bookings, and clamps at zero so the count can never go negative.
     *
     * <p><strong>Must run within the caller's transaction</strong> so the locks are
     * held through commit.
     *
     * @param roomType the room type whose nights to release.
     * @param checkIn  the first night to release (inclusive).
     * @param checkOut the checkout date (exclusive); assumed strictly after
     *                 {@code checkIn}.
     */
    public void release(RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
        List<RoomTypeInventory> rows = inventoryRepository.lockForStay(
                roomType.getId(), checkIn, checkOut);
        for (RoomTypeInventory row : rows) {
            row.setBookedCount(Math.max(0, row.getBookedCount() - 1));
        }
        inventoryRepository.saveAll(rows);
    }

    /**
     * Removes one room of capacity from the sellable allotment for every night in
     * {@code [start, end)} — used when a physical room is put under maintenance.
     *
     * <p>This keeps the count calendar in step with physical reality, so
     * {@link #availableCount} can never promise a room that check-in cannot actually
     * deliver. Nights with no inventory row (never opened for sale) are simply
     * skipped. Acquires the pessimistic write lock so the capacity decrement is
     * atomic against concurrent bookings.
     *
     * <p><strong>Must run within the caller's transaction</strong> so the locks are
     * held through commit; on rejection the whole range rolls back.
     *
     * @param roomType the room type losing a physical room.
     * @param start    the first night to reduce (inclusive).
     * @param end      the exclusive end of the maintenance range.
     * @throws NoAvailabilityException if any night is already fully sold
     *         ({@code remaining <= 0}); dropping capacity there would push total
     *         rooms below the booked count and oversell an existing guest, so that
     *         room must be freed or relocated first.
     */
    public void decrementCapacity(RoomType roomType, LocalDate start, LocalDate end) {
        List<RoomTypeInventory> rows = inventoryRepository.lockForStay(
                roomType.getId(), start, end);

        for (RoomTypeInventory row : rows) {
            if (row.remaining() <= 0) {
                throw new NoAvailabilityException(String.format(
                        "Cannot block a room of type '%s' on %s: all rooms are booked that night. "
                        + "Free or relocate the affected reservation first.",
                        roomType.getName(), row.getDate()));
            }
            row.setTotalRooms(row.getTotalRooms() - 1);
        }
        inventoryRepository.saveAll(rows);
    }

    /**
     * Returns one room of capacity per night in {@code [start, end)} to the
     * allotment — the inverse of {@link #decrementCapacity}, used when a maintenance
     * block is lifted.
     *
     * <p>Acquires the pessimistic write lock so the capacity increment is atomic
     * against concurrent bookings. <strong>Must run within the caller's
     * transaction</strong> so the locks are held through commit.
     *
     * @param roomType the room type regaining a physical room.
     * @param start    the first night to restore (inclusive).
     * @param end      the exclusive end of the range being unblocked.
     */
    public void incrementCapacity(RoomType roomType, LocalDate start, LocalDate end) {
        List<RoomTypeInventory> rows = inventoryRepository.lockForStay(
                roomType.getId(), start, end);
        for (RoomTypeInventory row : rows) {
            row.setTotalRooms(row.getTotalRooms() + 1);
        }
        inventoryRepository.saveAll(rows);
    }
}
