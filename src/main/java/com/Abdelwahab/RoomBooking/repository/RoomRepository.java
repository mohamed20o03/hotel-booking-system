package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Room;

/**
 * Data-access layer for the {@link Room} aggregate — an individual physical room that
 * belongs to a {@link com.Abdelwahab.RoomBooking.model.RoomType} and is the unit
 * actually assigned to a guest at check-in.
 *
 * <p><strong>Aggregate role.</strong> Where {@code RoomTypeInventory} tracks
 * availability as a count per night, a {@code Room} is a concrete, assignable
 * resource. This repository answers the physical-assignment question: which rooms of
 * a given type are genuinely free to hand to a guest for a stay, excluding those
 * already reserved or withdrawn for maintenance.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * Finds the physical rooms of a given type that are assignable for the stay
     * {@code [newCheckIn, newCheckOut)} — used at check-in to pick a concrete room to
     * hand to the guest, and to detect the case where an inventory count holds a
     * booking but no physical room is actually free.
     *
     * <p><strong>Hand-written rationale.</strong> This cannot be expressed as a
     * derived query: it filters rooms by the <em>absence</em> of conflicting rows in
     * two other tables. The JPQL uses a pair of correlated {@code NOT EXISTS}
     * subqueries, giving a set-difference semantics — start from all rooms of the
     * type, then subtract any room that has a blocking row:
     * <ul>
     *   <li><strong>Reservation conflict.</strong> Excludes a room if some reservation
     *       is assigned to it whose date span overlaps the requested stay, using the
     *       half-open overlap test {@code res.checkInDate < newCheckOut AND
     *       res.checkOutDate > newCheckIn}. Cancelled reservations
     *       ({@code status != 'CANCELLED'}) are ignored, since they no longer hold the
     *       room.</li>
     *   <li><strong>Maintenance conflict.</strong> Excludes a room that has a
     *       {@link com.Abdelwahab.RoomBooking.model.MaintenanceBlock} overlapping the
     *       stay, using the same half-open test {@code mb.startDate < newCheckOut AND
     *       mb.endDate > newCheckIn}. This mirrors
     *       {@link MaintananceBlockRepository#existsOverlappingBlock} so boundary-touching
     *       ranges are treated consistently.</li>
     * </ul>
     * A room survives only if it clears <em>both</em> exclusions. The half-open
     * convention means a checkout and a same-day check-in on the same room do not
     * conflict.
     *
     * @param roomTypeId the identifier of the {@link com.Abdelwahab.RoomBooking.model.RoomType}
     *        to draw candidate rooms from.
     * @param newCheckIn the first night of the requested stay, inclusive.
     * @param newCheckOut the departure date of the requested stay, exclusive.
     * @return the rooms of that type free for the entire stay; an empty list if every
     *         room is either booked or under maintenance for some overlapping night.
     *         Never {@code null}.
     */
    @Query("SELECT r FROM Room r WHERE r.roomType.id = :roomTypeId " +
       "AND NOT EXISTS (" +
       "    SELECT 1 FROM Reservation res WHERE res.assignedRoom = r " + 
       "    AND res.checkInDate < :newCheckOut AND res.checkOutDate > :newCheckIn " +
       "    AND res.status != 'CANCELLED'" + // Make sure to ignore cancelled reservations!
       ") " +
       "AND NOT EXISTS (" +
       "    SELECT 1 FROM MaintenanceBlock mb WHERE mb.room = r " + 
       "    AND mb.startDate < :newCheckOut AND mb.endDate > :newCheckIn" +
       ")")
    public List<Room> findAvailableRooms(
        @Param("roomTypeId") Long roomTypeId,
        @Param("newCheckIn") LocalDate newCheckIn,
        @Param("newCheckOut") LocalDate newCheckOut
    );

}