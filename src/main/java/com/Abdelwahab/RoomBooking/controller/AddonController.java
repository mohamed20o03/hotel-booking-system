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

import com.Abdelwahab.RoomBooking.dto.AddonRequestDTO;
import com.Abdelwahab.RoomBooking.dto.AddonResponseDTO;
import com.Abdelwahab.RoomBooking.service.AddonService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * A hotel's add-on catalogue. Reads are public (guests browse available add-ons);
 * writes are staff-only. Nesting under /api/hotels/** means the POST/PUT/DELETE
 * rules in SecurityConfig already gate the writes to admins; @PreAuthorize repeats
 * the check at each method so the guard survives a URL-rule refactor.
 */
@RestController
@RequestMapping("/api/hotels/{hotelId}/addons")
@RequiredArgsConstructor
public class AddonController {

    private final AddonService addonService;

    // GET /api/hotels/{hotelId}/addons — public: available add-ons for booking.
    @GetMapping
    public ResponseEntity<List<AddonResponseDTO>> getAvailableAddons(@PathVariable Long hotelId) {
        return ResponseEntity.ok(addonService.getAvailableAddons(hotelId));
    }

    // POST /api/hotels/{hotelId}/addons — admin only.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AddonResponseDTO> createAddon(
            @PathVariable Long hotelId, @Valid @RequestBody AddonRequestDTO request) {
        AddonResponseDTO created = addonService.createAddon(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // PUT /api/hotels/{hotelId}/addons/{addonId} — admin only.
    @PutMapping("/{addonId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AddonResponseDTO> updateAddon(
            @PathVariable Long hotelId, @PathVariable Long addonId,
            @Valid @RequestBody AddonRequestDTO request) {
        return ResponseEntity.ok(addonService.updateAddon(hotelId, addonId, request));
    }

    // DELETE /api/hotels/{hotelId}/addons/{addonId} — admin only.
    @DeleteMapping("/{addonId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAddon(
            @PathVariable Long hotelId, @PathVariable Long addonId) {
        addonService.deleteAddon(hotelId, addonId);
        return ResponseEntity.noContent().build();
    }
}
