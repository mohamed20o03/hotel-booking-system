package com.Abdelwahab.RoomBooking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.MaintenanceBlockRequestDTO;
import com.Abdelwahab.RoomBooking.dto.MaintenanceBlockResponseDTO;
import com.Abdelwahab.RoomBooking.service.MaintenanceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Staff endpoints for taking rooms in and out of service.
 *
 * These are administrative actions, restricted to ROLE_ADMIN. Enforcement is
 * layered: SecurityConfig gates /api/maintenance/** by role, and @PreAuthorize
 * repeats the check here so the guard survives a URL rule refactor.
 */
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    // POST /api/maintenance
    // Puts a room under maintenance and reduces sellable capacity for the range.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MaintenanceBlockResponseDTO> createBlock(
            @Valid @RequestBody MaintenanceBlockRequestDTO request) {
        MaintenanceBlockResponseDTO response = maintenanceService.createBlock(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // DELETE /api/maintenance/{id}
    // Lifts a maintenance block and returns the freed capacity to the allotment.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeBlock(@PathVariable Long id) {
        maintenanceService.removeBlock(id);
        return ResponseEntity.noContent().build();
    }
}
