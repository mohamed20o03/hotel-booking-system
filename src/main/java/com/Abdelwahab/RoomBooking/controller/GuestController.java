package com.Abdelwahab.RoomBooking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.GuestResponseDTO;
import com.Abdelwahab.RoomBooking.service.GuestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/guests")
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;

    // POST /api/guests/register
    @PostMapping("/register")
    public ResponseEntity<GuestResponseDTO> registerGuest(@Valid @RequestBody GuestRequestDTO request) {
        GuestResponseDTO response = guestService.registerGuest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/guests/{id}
    @GetMapping("/{id}")
    public ResponseEntity<GuestResponseDTO> getGuestById(@PathVariable Long id) {
        GuestResponseDTO response = guestService.getGuestById(id);
        return ResponseEntity.ok(response);
    }
}
