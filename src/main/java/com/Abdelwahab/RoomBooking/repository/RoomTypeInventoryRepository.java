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

@Repository
public interface RoomTypeInventoryRepository extends JpaRepository<RoomTypeInventory, Long> {

    /**
     * Read-only view of the inventory rows for a stay [checkIn, checkOut).
     * Used by availability search — no lock, so it never blocks bookings.
     */
    @Query("SELECT i FROM RoomTypeInventory i " +
           "WHERE i.roomType.id = :roomTypeId AND i.date >= :checkIn AND i.date < :checkOut")
    List<RoomTypeInventory> findForStay(
        @Param("roomTypeId") Long roomTypeId,
        @Param("checkIn") LocalDate checkIn,
        @Param("checkOut") LocalDate checkOut);

    /**
     * Locks the inventory rows for a stay for the duration of the current
     * transaction (SELECT ... FOR UPDATE). Ordered by date so concurrent
     * bookings always acquire locks in the same order, avoiding deadlocks.
     *
     * Callers hold these locks until commit, so the check-then-increment of
     * booked_count is atomic against other bookers competing for the same nights.
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
