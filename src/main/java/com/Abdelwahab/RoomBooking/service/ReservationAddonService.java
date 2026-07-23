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
import lombok.extern.slf4j.Slf4j;

/**
 * Attaches optional add-ons (spa, transfer, ...) to a reservation and keeps the
 * reservation's frozen total in step.
 *
 * <p><strong>Responsibility.</strong> Manages the {@link ReservationAddon} lines on
 * a booking and adjusts the reservation's stored total as lines are added or
 * removed, so the amount the guest ultimately pays reflects the extras.
 *
 * <p><strong>State-machine constraint.</strong> Add-ons may only be changed while
 * the reservation is a live {@code PENDING} hold — once it is paid
 * ({@code CONFIRMED}) the total has been settled, so changing extras would desync
 * what the guest paid from what they owe. Every mutating call therefore requires a
 * {@code PENDING} status.
 *
 * <p><strong>Price integrity.</strong> The add-on's unit price is frozen from the
 * catalogue at attach time and never trusted from the client, so a later catalogue
 * price change never rewrites an existing booking's line.
 *
 * <p><strong>Security &amp; scope.</strong> Every operation is ownership-scoped: the
 * caller is resolved from the security context and must own the reservation, which
 * defends against tampering with another guest's booking (IDOR).
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton holding only its
 * injected repositories; safe for concurrent request threads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationAddonService {

    private final ReservationRepository reservationRepository;
    private final ReservationAddonRepository reservationAddonRepository;
    private final AddonRepository addonRepository;

    /**
     * Attaches an add-on line to the caller's own {@code PENDING} reservation and
     * rolls its cost into the reservation total.
     *
     * <p>Validates that the reservation is owned by the authenticated guest and still
     * {@code PENDING}, that the add-on belongs to the same hotel as the reservation,
     * and that the add-on is currently available. The unit price is frozen from the
     * catalogue (never taken from the request). Read-write transactional: the new
     * line and the bumped reservation total commit together, so payment will collect
     * the added amount.
     *
     * @param reservationId the reservation to extend; must be owned by the caller and
     *                      in {@code PENDING} state.
     * @param request       the add-on selection (add-on id and quantity); must be
     *                      non-{@code null} and valid.
     * @return a {@link ReservationAddonResponseDTO} view of the created line,
     *         including its frozen unit price and line total.
     * @throws ResourceNotFoundException if the reservation or the add-on does not
     *         exist.
     * @throws IllegalArgumentException  if the caller does not own the reservation,
     *         the reservation is not {@code PENDING}, the add-on belongs to a
     *         different hotel, or the add-on is not available. Rolls back the
     *         transaction so neither the line nor the total change persists.
     */
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

        log.info("Add-on attached [confirmation={} addonId={} lineId={} qty={} lineTotal={} newTotal={}]",
                reservation.getConfirmationNumber(), addon.getId(), line.getId(),
                line.getQuantity(), lineTotal(line), reservation.getTotalPrice());
        return toDTO(line);
    }

    /**
     * Lists the add-on lines attached to the caller's own reservation.
     *
     * <p>Ownership is enforced before reading, so a guest can only see the extras on
     * their own booking regardless of its status. Read-only transactional: a pure
     * query, marked {@code readOnly} to skip dirty-checking overhead.
     *
     * @param reservationId the reservation whose add-ons to list; must be owned by
     *                      the caller.
     * @return the add-on lines as {@link ReservationAddonResponseDTO} views; an empty
     *         list if none are attached.
     * @throws ResourceNotFoundException if the reservation does not exist.
     * @throws IllegalArgumentException  if the caller does not own the reservation.
     */
    @Transactional(readOnly = true)
    public List<ReservationAddonResponseDTO> getAddons(Long reservationId) {
        requireOwnedReservation(reservationId);
        return reservationAddonRepository.findByReservationId(reservationId).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Removes an add-on line from the caller's own {@code PENDING} reservation and
     * subtracts its cost from the reservation total.
     *
     * <p>Requires ownership and a {@code PENDING} status, and verifies the line
     * actually belongs to the named reservation before removing it. Read-write
     * transactional: the reduced total and the line deletion commit together.
     *
     * @param reservationId      the reservation to modify; must be owned by the
     *                           caller and in {@code PENDING} state.
     * @param reservationAddonId the add-on line to remove; must belong to that
     *                           reservation.
     * @throws ResourceNotFoundException if the reservation or the line does not
     *         exist, or the line belongs to a different reservation.
     * @throws IllegalArgumentException  if the caller does not own the reservation or
     *         it is not {@code PENDING}.
     */
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
        log.info("Add-on detached [confirmation={} lineId={} newTotal={}]",
                reservation.getConfirmationNumber(), reservationAddonId, reservation.getTotalPrice());
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
