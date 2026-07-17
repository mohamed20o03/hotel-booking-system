package com.Abdelwahab.RoomBooking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.ReservationAddonRequestDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationAddonResponseDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationConfirmationDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationRequestDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationResponseDTO;
import com.Abdelwahab.RoomBooking.service.ReservationAddonService;
import com.Abdelwahab.RoomBooking.service.ReservationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationAddonService reservationAddonService;

    // POST /api/reservations
    // Creates a new reservation for a guest
    @PostMapping
    public ResponseEntity<ReservationConfirmationDTO> createBooking(@Valid @RequestBody ReservationRequestDTO request) {
        ReservationConfirmationDTO responseDTO = reservationService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    // GET /api/reservations/{confirmationNumber}
    // Looks up a single reservation by its confirmation number (e.g. "AB12CD34")
    @GetMapping("/{confirmationNumber}")
    public ResponseEntity<ReservationResponseDTO> getByConfirmationNumber(@PathVariable String confirmationNumber) {
        ReservationResponseDTO reservation = reservationService.getReservationByConfirmationNumber(confirmationNumber);
        return ResponseEntity.ok(reservation);
    }

    // GET /api/reservations/my-reservations
    // Returns all reservations for the currently authenticated guest
    @GetMapping("/my-reservations")
    public ResponseEntity<List<ReservationResponseDTO>> getMyReservations() {
        List<ReservationResponseDTO> reservations = reservationService.getMyReservations();
        return ResponseEntity.ok(reservations);
    }

    // PATCH /api/reservations/{id}/cancel
    // Cancels a confirmed reservation.
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long id) {
        reservationService.cancelReservation(id);
        return ResponseEntity.noContent().build();
    }

    // PATCH /api/reservations/{id}/check-in
    // Assigns a physical room and moves a CONFIRMED reservation to CHECKED_IN.
    // Front-desk action — admin only.
    @PatchMapping("/{id}/check-in")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReservationResponseDTO> checkIn(@PathVariable Long id) {
        ReservationResponseDTO reservation = reservationService.checkIn(id);
        return ResponseEntity.ok(reservation);
    }

    // ── Add-ons on a reservation (guest, while PENDING) ──────────────

    // POST /api/reservations/{id}/addons
    // Attaches an add-on to the guest's own PENDING reservation, bumping the total.
    @PostMapping("/{id}/addons")
    public ResponseEntity<ReservationAddonResponseDTO> attachAddon(
            @PathVariable Long id, @Valid @RequestBody ReservationAddonRequestDTO request) {
        ReservationAddonResponseDTO line = reservationAddonService.attachAddon(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(line);
    }

    // GET /api/reservations/{id}/addons
    // Lists the add-ons attached to the guest's own reservation.
    @GetMapping("/{id}/addons")
    public ResponseEntity<List<ReservationAddonResponseDTO>> getAddons(@PathVariable Long id) {
        return ResponseEntity.ok(reservationAddonService.getAddons(id));
    }

    // DELETE /api/reservations/{id}/addons/{addonLineId}
    // Removes an attached add-on from the guest's own PENDING reservation.
    @DeleteMapping("/{id}/addons/{addonLineId}")
    public ResponseEntity<Void> detachAddon(
            @PathVariable Long id, @PathVariable Long addonLineId) {
        reservationAddonService.detachAddon(id, addonLineId);
        return ResponseEntity.noContent().build();
    }

}
