package com.Abdelwahab.RoomBooking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.BookingRequestDTO;
import com.Abdelwahab.RoomBooking.dto.BookingResponseDTO;
import com.Abdelwahab.RoomBooking.service.BookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {
    
    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponseDTO> createBooking (@Valid @RequestBody BookingRequestDTO request) {
        BookingResponseDTO responseDTO = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }
}
