package com.Abdelwahab.RoomBooking.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.ReservationAddonRequestDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationAddonResponseDTO;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Addon;
import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.ReservationAddon;
import com.Abdelwahab.RoomBooking.model.ReservationStatus;
import com.Abdelwahab.RoomBooking.repository.AddonRepository;
import com.Abdelwahab.RoomBooking.repository.ReservationAddonRepository;
import com.Abdelwahab.RoomBooking.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Attaches optional add-ons (spa, transfer, ...) to a reservation and keeps the
 * reservation's frozen total in step.
 *
 * Add-ons may only be changed while the reservation is a live PENDING hold — once
 * it is paid (CONFIRMED) the total has been settled, so changing extras would
 * desync what the guest paid from what they owe. The add-on's unit price is frozen
 * from the catalogue at attach time (never trusted from the client), so a later
 * catalogue price change never rewrites an existing booking's line.
 */
@Service
@RequiredArgsConstructor
public class ReservationAddonService {

    private final ReservationRepository reservationRepository;
    private final ReservationAddonRepository reservationAddonRepository;
    private final AddonRepository addonRepository;

    @Transactional
    public ReservationAddonResponseDTO attachAddon(Long reservationId, ReservationAddonRequestDTO request) {
        Reservation reservation = requireOwnedModifiableReservation(reservationId);

        Addon addon = addonRepository.findById(request.addonId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Add-on not found with ID: " + request.addonId()));

        // An add-on can only be attached to a reservation of the same hotel.
        Long reservationHotelId = reservation.getRatePlan().getRoomType().getHotel().getId();
        if (!addon.getHotel().getId().equals(reservationHotelId)) {
            throw new IllegalArgumentException(String.format(
                    "Add-on %d belongs to a different hotel than this reservation.", addon.getId()));
        }
        if (!Boolean.TRUE.equals(addon.getAvailable())) {
            throw new IllegalArgumentException(String.format(
                    "Add-on '%s' is not currently available.", addon.getName()));
        }

        // Freeze the price from the catalogue — the client never sends a price.
        BigDecimal unitPrice = addon.getPrice();
        ReservationAddon line = ReservationAddon.builder()
                .reservation(reservation)
                .addon(addon)
                .quantity(request.quantity())
                .unitPrice(unitPrice)
                .createdAt(LocalDateTime.now())
                .build();
        line = reservationAddonRepository.save(line);

        // Roll the new line into the reservation's frozen total so payment collects it.
        reservation.setTotalPrice(
                reservation.getTotalPrice().add(lineTotal(line)));
        reservationRepository.save(reservation);

        return toDTO(line);
    }

    @Transactional(readOnly = true)
    public List<ReservationAddonResponseDTO> getAddons(Long reservationId) {
        requireOwnedReservation(reservationId);
        return reservationAddonRepository.findByReservationId(reservationId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public void detachAddon(Long reservationId, Long reservationAddonId) {
        Reservation reservation = requireOwnedModifiableReservation(reservationId);

        ReservationAddon line = reservationAddonRepository.findById(reservationAddonId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation add-on not found with ID: " + reservationAddonId));

        if (!line.getReservation().getId().equals(reservationId)) {
            throw new ResourceNotFoundException(String.format(
                    "Add-on line %d does not belong to reservation %d.", reservationAddonId, reservationId));
        }

        // Subtract the line from the frozen total, then remove it.
        reservation.setTotalPrice(
                reservation.getTotalPrice().subtract(lineTotal(line)));
        reservationRepository.save(reservation);
        reservationAddonRepository.delete(line);
    }

    // ── helpers ───────────────────────────────────────────────────

    /** Loads a reservation and asserts the authenticated guest owns it. */
    private Reservation requireOwnedReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found with ID: " + reservationId));

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!reservation.getGuest().getEmail().equals(currentEmail)) {
            throw new IllegalArgumentException("You do not have permission to modify this reservation.");
        }
        return reservation;
    }

    /** As above, but also requires the reservation still be a mutable PENDING hold. */
    private Reservation requireOwnedModifiableReservation(Long reservationId) {
        Reservation reservation = requireOwnedReservation(reservationId);
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalArgumentException(String.format(
                    "Add-ons can only be changed while a reservation is PENDING. Current status: %s.",
                    reservation.getStatus()));
        }
        return reservation;
    }

    private BigDecimal lineTotal(ReservationAddon line) {
        return line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
    }

    private ReservationAddonResponseDTO toDTO(ReservationAddon line) {
        return new ReservationAddonResponseDTO(
                line.getId(),
                line.getAddon().getId(),
                line.getAddon().getName(),
                line.getQuantity(),
                line.getUnitPrice(),
                lineTotal(line));
    }
}
