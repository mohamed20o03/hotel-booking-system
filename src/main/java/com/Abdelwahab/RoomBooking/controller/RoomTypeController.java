package com.Abdelwahab.RoomBooking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.RoomTypeResponseDTO;
import com.Abdelwahab.RoomBooking.service.RoomTypeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hotels/{hotelId}/room-types")
@RequiredArgsConstructor
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    // GET /api/hotels/{hotelId}/room-types
    @GetMapping
    public ResponseEntity<List<RoomTypeResponseDTO>> getRoomTypesByHotel(@PathVariable Long hotelId) {
        List<RoomTypeResponseDTO> roomTypes = roomTypeService.getRoomTypesByHotel(hotelId);
        return ResponseEntity.ok(roomTypes);
    }
}
