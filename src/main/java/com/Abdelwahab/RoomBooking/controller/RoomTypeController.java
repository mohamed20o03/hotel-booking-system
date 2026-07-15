package com.Abdelwahab.RoomBooking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.RoomTypeRequestDTO;
import com.Abdelwahab.RoomBooking.dto.RoomTypeResponseDTO;
import com.Abdelwahab.RoomBooking.service.RoomTypeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hotels/{hotelId}/room-types")
@RequiredArgsConstructor
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    // GET /api/hotels/{hotelId}/room-types — public browsing
    @GetMapping
    public ResponseEntity<List<RoomTypeResponseDTO>> getRoomTypesByHotel(@PathVariable Long hotelId) {
        List<RoomTypeResponseDTO> roomTypes = roomTypeService.getRoomTypesByHotel(hotelId);
        return ResponseEntity.ok(roomTypes);
    }

    // POST /api/hotels/{hotelId}/room-types — admin only
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomTypeResponseDTO> createRoomType(
            @PathVariable Long hotelId,
            @Valid @RequestBody RoomTypeRequestDTO request) {
        RoomTypeResponseDTO created = roomTypeService.createRoomType(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // PUT /api/hotels/{hotelId}/room-types/{id} — admin only
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomTypeResponseDTO> updateRoomType(
            @PathVariable Long hotelId,
            @PathVariable Long id,
            @Valid @RequestBody RoomTypeRequestDTO request) {
        RoomTypeResponseDTO updated = roomTypeService.updateRoomType(hotelId, id, request);
        return ResponseEntity.ok(updated);
    }

    // DELETE /api/hotels/{hotelId}/room-types/{id} — admin only
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoomType(
            @PathVariable Long hotelId,
            @PathVariable Long id) {
        roomTypeService.deleteRoomType(hotelId, id);
        return ResponseEntity.noContent().build();
    }
}
