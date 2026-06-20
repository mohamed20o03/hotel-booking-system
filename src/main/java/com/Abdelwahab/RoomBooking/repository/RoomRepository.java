package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Room;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

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