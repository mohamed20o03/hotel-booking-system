package com.Abdelwahab.RoomBooking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.ReservationConfirmationDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationRequestDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationResponseDTO;
import com.Abdelwahab.RoomBooking.service.ReservationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

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

}
