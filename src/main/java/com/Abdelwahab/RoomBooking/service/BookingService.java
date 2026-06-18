package com.Abdelwahab.RoomBooking.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.Abdelwahab.RoomBooking.dto.BookingRequestDTO;
import com.Abdelwahab.RoomBooking.dto.BookingResponseDTO;
import com.Abdelwahab.RoomBooking.model.Booking;
import com.Abdelwahab.RoomBooking.model.Room;
import com.Abdelwahab.RoomBooking.model.User;
import com.Abdelwahab.RoomBooking.repository.BookingRepository;
import com.Abdelwahab.RoomBooking.repository.RoomRepository;
import com.Abdelwahab.RoomBooking.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional
    public  BookingResponseDTO createBooking(BookingRequestDTO request) {

        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new RuntimeException("User not found"));

        Room room = roomRepository.findById(request.roomId())
            .orElseThrow(() -> new RuntimeException("Room not found"));

        List<Booking> overlapBookings = bookingRepository.findOverlappingBookings(
            request.roomId(), request.startDate(), request.endDate()
        );

        if(!overlapBookings.isEmpty()) {
            throw new RuntimeException("Room is already booked for this time!");
        }

        Booking newBooking = Booking.builder()
            .startDate(request.startDate())
            .endDate(request.endDate())
            .user(user)
            .room(room)
            .build();

        Booking savedBooking = bookingRepository.save(newBooking);
        
        return new BookingResponseDTO(
            savedBooking.getId(),
            room.getId(),
            user.getId(),
            savedBooking.getStartDate(),
            savedBooking.getEndDate(),
            "CONFIRMED"
        );
    }
}
