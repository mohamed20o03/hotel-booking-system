package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.MaintenanceBlock;

/**
 * Data-access layer for the {@link MaintenanceBlock} aggregate — a date range during
 * which a physical {@link com.Abdelwahab.RoomBooking.model.Room} is withdrawn from
 * sale (cleaning, repair, or refurbishment).
 *
 * <p><strong>Aggregate role.</strong> Maintenance blocks are the operational
 * complement to reservations: both remove a room from availability for a span of
 * nights, and the availability search in
 * {@link RoomRepository#findAvailableRooms} excludes rooms carrying either. This
 * repository's overlap check protects the invariant that a room is not blocked twice
 * for the same nights, which would otherwise decrement effective capacity incorrectly.
 *
 * @see RoomRepository#findAvailableRooms
 */
@Repository
public interface MaintananceBlockRepository extends JpaRepository<MaintenanceBlock, Long>{

    /**
     * Reports whether a room already has a maintenance block overlapping the proposed
     * range, so a new block is not created on top of an existing one for the same
     * physical room and nights.
     *
     * <p><strong>Hand-written rationale.</strong> The method uses an explicit JPQL
     * {@code CASE WHEN COUNT(mb) > 0} existence probe rather than a derived
     * {@code existsBy...} finder because the overlap predicate is not expressible as a
     * simple property comparison. The test is half-open — {@code existing.startDate <
     * newEnd AND existing.endDate > newStart} — which deliberately matches the range
     * logic in {@link RoomRepository#findAvailableRooms}, so two blocks that merely
     * touch at a boundary date (one ending the day another begins) are <em>not</em>
     * treated as overlapping. Pushing the check to the database avoids loading block
     * rows into memory just to test intersection.
     *
     * @param roomId the identifier of the physical
     *        {@link com.Abdelwahab.RoomBooking.model.Room} being blocked.
     * @param start the first night of the proposed block, inclusive.
     * @param end the end of the proposed block, exclusive.
     * @return {@code true} if any existing block on that room intersects
     *         {@code [start, end)}; {@code false} if the range is clear.
     */
    @Query("SELECT CASE WHEN COUNT(mb) > 0 THEN true ELSE false END FROM MaintenanceBlock mb "
         + "WHERE mb.room.id = :roomId AND mb.startDate < :end AND mb.endDate > :start")
    boolean existsOverlappingBlock(
        @Param("roomId") Long roomId,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end);
}
