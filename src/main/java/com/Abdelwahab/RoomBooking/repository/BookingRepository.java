package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.Abdelwahab.RoomBooking.model.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long>{

    public List<Booking> findByRoomId(Long roomId);

    public List<Booking> findByUserId(Long userId);

    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId AND b.startDate < :newEndDate AND b.endDate > :newStartDate")
    public List<Booking> findOverlappingBookings(
        @Param("roomId") Long roomId, 
        @Param("newStartDate") LocalDateTime newStartDate, 
        @Param("newEndDate") LocalDateTime newEndDate
    );
} 
