package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.MaintenanceBlock;

@Repository
public interface MaintanceBlockRepository extends JpaRepository<MaintenanceBlock, Long>{

    /**
     * True if the given room already has a maintenance block overlapping
     * [start, end). Half-open overlap test (existing.start < new.end AND
     * existing.end > new.start), matching RoomRepository.findAvailableRooms, so two
     * blocks that merely touch at a boundary date are not considered overlapping.
     *
     * Guards against decrementing a room type's capacity twice for the same
     * physical room over the same nights.
     */
    @Query("SELECT CASE WHEN COUNT(mb) > 0 THEN true ELSE false END FROM MaintenanceBlock mb "
         + "WHERE mb.room.id = :roomId AND mb.startDate < :end AND mb.endDate > :start")
    boolean existsOverlappingBlock(
        @Param("roomId") Long roomId,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end);
}
