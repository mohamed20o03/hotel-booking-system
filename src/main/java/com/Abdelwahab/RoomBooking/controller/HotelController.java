package com.Abdelwahab.RoomBooking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.HotelResponseDTO;
import com.Abdelwahab.RoomBooking.service.HotelService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    // GET /api/hotels
    @GetMapping
    public ResponseEntity<List<HotelResponseDTO>> getAllHotels() {
        List<HotelResponseDTO> hotels = hotelService.getAllHotels();
        return ResponseEntity.ok(hotels);
    }

    // GET /api/hotels/{id}
    @GetMapping("/{id}")
    public ResponseEntity<HotelResponseDTO> getHotelById(@PathVariable Long id) {
        HotelResponseDTO hotel = hotelService.getHotelById(id);
        return ResponseEntity.ok(hotel);
    }
}
