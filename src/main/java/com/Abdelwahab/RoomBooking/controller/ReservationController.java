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
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP entry point for the reservation lifecycle: booking, retrieval, guest
 * cancellation, front-desk check-in, and per-reservation add-ons.
 *
 * <p><strong>Architectural role.</strong> A thin web-contract adapter. It binds and
 * validates requests, delegates to {@link ReservationService} /
 * {@link ReservationAddonService}, and maps results to HTTP status codes. It holds
 * no business logic: state-machine rules, pricing, inventory, and ownership checks
 * all live in the service layer and are covered by their own tests.
 *
 * <p><strong>Thread safety.</strong> Stateless and therefore thread-safe. Its only
 * fields are the injected singleton services; each request runs on its own thread
 * with request-scoped arguments.
 *
 * <p><strong>Security &amp; scope.</strong> This controller mixes three access
 * patterns on one path prefix, which is why it is the reference for HTTP-layer
 * tests:
 * <ul>
 *   <li><strong>Authenticated, any role</strong> — booking, my-reservations,
 *       cancel, and the add-on endpoints. Enforced by the
 *       {@code anyRequest().authenticated()} default in {@code SecurityConfig}.</li>
 *   <li><strong>Admin only</strong> — {@link #checkIn(Long)} is gated both by a URL
 *       rule and by {@code @PreAuthorize}, being a front-desk action.</li>
 *   <li><strong>Ownership (IDOR) scoped</strong> — cancel and the add-on endpoints
 *       operate only on the caller's own reservation. That row-level check is
 *       enforced in the service, not expressible as a role.</li>
 * </ul>
 *
 * <p><strong>Error contract.</strong> Domain exceptions are mapped centrally by
 * {@code GlobalExceptionHandler}: {@code ResourceNotFoundException → 404},
 * invalid-state / ownership {@code IllegalArgumentException → 400},
 * {@code NoAvailabilityException → 409}, and validation failures {@code → 400}.
 *
 * @see ReservationService
 * @see com.Abdelwahab.RoomBooking.exception.GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Slf4j
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationAddonService reservationAddonService;

    /**
     * Creates a reservation, holding room-type inventory for the requested stay and
     * returning a confirmation in the {@code PENDING} state (payment confirms it
     * later).
     *
     * <p>Requires an authenticated guest; the booking is attributed to the caller.
     * The request body is validated with {@code @Valid} before the service is
     * reached.
     *
     * @param request the desired stay — rate plan, check-in/out dates, and guest
     *                count; dates must be present/future and check-out after
     *                check-in (enforced by DTO constraints).
     * @return {@code 201 Created} with the {@link ReservationConfirmationDTO} for the
     *         new hold.
     * @throws com.Abdelwahab.RoomBooking.exception.NoAvailabilityException if no
     *         inventory remains for a night in the range (mapped to {@code 409}).
     * @throws IllegalArgumentException on an invalid date range (mapped to
     *         {@code 400}).
     */
    @PostMapping
    public ResponseEntity<ReservationConfirmationDTO> createBooking(@Valid @RequestBody ReservationRequestDTO request) {
        log.debug("POST /api/reservations [ratePlanId={} checkIn={} checkOut={}]",
                request.ratePlanId(), request.checkInDate(), request.checkOutDate());
        ReservationConfirmationDTO responseDTO = reservationService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    /**
     * Looks up a single reservation by its human-facing confirmation number
     * (e.g. {@code "AB12CD34"}), the value a guest quotes when managing a booking.
     *
     * <p>Requires authentication. Note this lookup is keyed by the confirmation
     * number rather than ownership, so it does not itself scope to the caller —
     * knowledge of the code is the access token here.
     *
     * @param confirmationNumber the opaque confirmation code issued at booking time.
     * @return {@code 200 OK} with the matching {@link ReservationResponseDTO}.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         reservation carries that confirmation number (mapped to {@code 404}).
     */
    @GetMapping("/{confirmationNumber}")
    public ResponseEntity<ReservationResponseDTO> getByConfirmationNumber(@PathVariable String confirmationNumber) {
        ReservationResponseDTO reservation = reservationService.getReservationByConfirmationNumber(confirmationNumber);
        return ResponseEntity.ok(reservation);
    }

    /**
     * Returns every reservation belonging to the currently authenticated guest,
     * newest stay first.
     *
     * <p><strong>Ownership-scoped:</strong> the guest is resolved from the security
     * context, not from a request parameter, so a caller can only ever see their own
     * bookings — there is no identifier to tamper with (IDOR-safe by construction).
     *
     * @return {@code 200 OK} with the caller's reservations; an empty list if they
     *         have none.
     */
    @GetMapping("/my-reservations")
    public ResponseEntity<List<ReservationResponseDTO>> getMyReservations() {
        List<ReservationResponseDTO> reservations = reservationService.getMyReservations();
        return ResponseEntity.ok(reservations);
    }

    /**
     * Cancels a live booking and releases its held inventory back to the allotment.
     *
     * <p><strong>Ownership-scoped:</strong> the service verifies the authenticated
     * guest owns this reservation before acting, so a guest cannot cancel someone
     * else's booking by guessing an id. Only {@code PENDING} and {@code CONFIRMED}
     * reservations hold inventory and are therefore cancellable; any other status is
     * an invalid transition.
     *
     * @param id the reservation to cancel.
     * @return {@code 204 No Content} on success.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if the
     *         id does not exist (mapped to {@code 404}).
     * @throws IllegalArgumentException if the caller does not own the reservation, or
     *         its status forbids cancellation (both mapped to {@code 400}).
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long id) {
        log.debug("PATCH /api/reservations/{}/cancel", id);
        reservationService.cancelReservation(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Checks a guest in: assigns a free physical room and moves a {@code CONFIRMED}
     * reservation to {@code CHECKED_IN}.
     *
     * <p><strong>Admin only.</strong> A front-desk action, gated twice — by the URL
     * rule in {@code SecurityConfig} and by {@code @PreAuthorize} here. Unlike
     * cancel, this is not ownership-scoped: staff act on any guest's booking.
     *
     * <p>Assumes a physical room of the reserved type is actually free for the stay;
     * an inventory count can hold a booking while a maintenance block leaves no room
     * assignable, which is treated as a conflict rather than a not-found.
     *
     * @param id the reservation to check in.
     * @return {@code 200 OK} with the updated reservation, now carrying its assigned
     *         room.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if the
     *         id does not exist (mapped to {@code 404}).
     * @throws IllegalArgumentException if the reservation is not in {@code CONFIRMED}
     *         state (mapped to {@code 400}).
     * @throws com.Abdelwahab.RoomBooking.exception.NoAvailabilityException if no
     *         physical room is free to assign (mapped to {@code 409}).
     */
    @PatchMapping("/{id}/check-in")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReservationResponseDTO> checkIn(@PathVariable Long id) {
        log.debug("PATCH /api/reservations/{}/check-in", id);
        ReservationResponseDTO reservation = reservationService.checkIn(id);
        return ResponseEntity.ok(reservation);
    }

    // ── Add-ons on a reservation (guest, while PENDING) ──────────────

    // POST /api/reservations/{id}/addons
    // Attaches an add-on to the guest's own PENDING reservation, bumping the total.
    @PostMapping("/{id}/addons")
    public ResponseEntity<ReservationAddonResponseDTO> attachAddon(
            @PathVariable Long id, @Valid @RequestBody ReservationAddonRequestDTO request) {
        log.debug("POST /api/reservations/{}/addons [addonId={} qty={}]",
                id, request.addonId(), request.quantity());
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
        log.debug("DELETE /api/reservations/{}/addons/{}", id, addonLineId);
        reservationAddonService.detachAddon(id, addonLineId);
        return ResponseEntity.noContent().build();
    }

}
