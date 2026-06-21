package com.Abdelwahab.RoomBooking.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.AvailabilityResponseDTO;
import com.Abdelwahab.RoomBooking.service.ReservationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class SearchController {

    private final ReservationService reservationService;

    // GET /api/hotels/{hotelId}/availability?checkIn=2026-07-01&checkOut=2026-07-05&guests=2
    // Returns available room types with their rate plans for the given criteria
    @GetMapping("/{hotelId}/availability")
    public ResponseEntity<List<AvailabilityResponseDTO>> searchAvailability(
            @PathVariable Long hotelId,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestParam int guests) {
        
        LocalDate checkInDate = LocalDate.parse(checkIn);
        LocalDate checkOutDate = LocalDate.parse(checkOut);
        
        List<AvailabilityResponseDTO> results = reservationService.searchAvailableOptions(
                hotelId, checkInDate, checkOutDate, guests);
                
        return ResponseEntity.ok(results);
    }
}
