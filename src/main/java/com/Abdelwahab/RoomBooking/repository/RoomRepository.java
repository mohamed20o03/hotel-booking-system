package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Room;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("SELECT r FROM Room r WHERE r.id NOT IN " +
           "(SELECT b.room.id FROM Booking b WHERE b.startDate < :newEndDate AND b.endDate > :newStartDate)")
    public List<Room> findEmptyRooms(
        @Param("newStartDate") LocalDateTime newStartDate,
        @Param("newEndDate") LocalDateTime newEndDate
    );
}