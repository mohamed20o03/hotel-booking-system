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
 * Owns the room-type allotment calendar (RoomTypeInventory).
 *
 * A room type is sold by COUNT per night, not by pinning a physical room at
 * booking time. All mutating methods here must run inside the caller's booking
 * transaction so the pessimistic locks they take are held until that
 * transaction commits.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final RoomTypeInventoryRepository inventoryRepository;

    /**
     * Non-locking availability check for search. Returns true only if every night
     * in [checkIn, checkOut) is open for sale (has an inventory row) AND still has
     * a room left. A missing row means the hotel has not opened that date, which
     * counts as unavailable.
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
     * The smallest number of rooms free on any single night of the stay — i.e.
     * how many more reservations of this type could still be taken for these
     * exact dates. Zero if any night is closed or sold out. Non-locking; for
     * display in search results.
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
     * Atomically reserves one room of the type for every night in the stay.
     *
     * Locks the night rows (SELECT ... FOR UPDATE) so a competing booking for the
     * same last room blocks until this transaction commits. Throws
     * NoAvailabilityException if any night is closed or sold out — rolling back
     * the whole booking.
     *
     * MUST be called within the booking transaction.
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
     * Releases one room per night back to the allotment (e.g. on cancellation).
     * Locks the rows so the decrement is atomic against concurrent bookings.
     * Never lets a count go below zero. MUST run within a transaction.
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
     * Takes one room of the type out of the sellable allotment for every night in
     * [start, end) — used when a physical room is put under maintenance. This keeps
     * the count calendar in step with physical reality, so availableCount can never
     * promise a room that check-in cannot actually deliver.
     *
     * Rejects the whole range if any night is already fully sold (remaining <= 0),
     * because dropping capacity there would push total_rooms below booked_count and
     * oversell an existing guest — that room must be freed/relocated first. Nights
     * with no inventory row (never opened for sale) are simply skipped.
     *
     * Locks the rows so the decrement is atomic against concurrent bookings.
     * MUST run within a transaction.
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
     * Returns one room of capacity per night in [start, end) to the allotment — the
     * inverse of decrementCapacity, used when a maintenance block is lifted.
     * Locks the rows so the increment is atomic against concurrent bookings.
     * MUST run within a transaction.
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
