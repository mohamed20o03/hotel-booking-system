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
 * HTTP entry point for taking physical rooms in and out of service: placing and
 * lifting maintenance blocks.
 *
 * <p><strong>Architectural role.</strong> A thin web-contract adapter. It binds and
 * validates requests, delegates to {@link MaintenanceService}, and maps results to
 * HTTP status codes. It holds no business logic: overlap detection and the coupled
 * adjustment of the room type's sellable capacity live in the service layer.
 *
 * <p><strong>Thread safety.</strong> Stateless and therefore thread-safe. Its only
 * field is the injected singleton {@link MaintenanceService}; each request runs on
 * its own thread with request-scoped arguments.
 *
 * <p><strong>Security &amp; scope.</strong> These are administrative, front-desk
 * actions. Enforcement is layered: {@code SecurityConfig} gates the whole
 * {@code /api/maintenance/**} prefix with {@code hasRole("ADMIN")}, and
 * {@code @PreAuthorize("hasRole('ADMIN')")} repeats the check on each method so the
 * guard survives a URL-rule refactor. An unauthenticated request yields
 * {@code 401 Unauthorized} (via {@code RestAuthenticationEntryPoint}), whereas an
 * authenticated non-admin request yields {@code 403 Forbidden}.
 *
 * <p><strong>Error contract.</strong> Domain exceptions are mapped centrally by
 * {@code GlobalExceptionHandler}: {@code ResourceNotFoundException → 404},
 * {@code DuplicateResourceException} (overlapping block) and
 * {@code NoAvailabilityException} (a blocked night is already fully sold)
 * {@code → 409}, bean-validation failures on {@code @Valid → 400}, authentication
 * failures {@code → 401}, and authorization failures {@code → 403}.
 *
 * @see MaintenanceService
 * @see com.Abdelwahab.RoomBooking.exception.GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    /**
     * Places a room under maintenance for the requested range, decrementing the room
     * type's sellable capacity for every blocked night.
     *
     * <p><strong>Admin only.</strong> The whole operation runs in one transaction, so
     * if any night is already fully sold nothing is persisted and no capacity changes.
     * The request body is validated with {@code @Valid} before the service is reached.
     *
     * @param request the block to create — the physical room, the {@code [startDate,
     *                endDate)} range, and a reason; validated by DTO constraints.
     * @return {@code 201 Created} with the persisted {@link MaintenanceBlockResponseDTO},
     *         including its generated id.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         room has the requested id (mapped to {@code 404}).
     * @throws com.Abdelwahab.RoomBooking.exception.DuplicateResourceException if the
     *         range overlaps an existing block on the same room (mapped to
     *         {@code 409}).
     * @throws com.Abdelwahab.RoomBooking.exception.NoAvailabilityException if a
     *         blocked night is already fully booked, leaving no capacity to withdraw
     *         (mapped to {@code 409}).
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if the body
     *         fails bean validation (mapped to {@code 400}).
     * @throws org.springframework.security.access.AccessDeniedException if the caller
     *         is authenticated but not an admin (mapped to {@code 403}); an
     *         unauthenticated caller is instead rejected with {@code 401} beforehand.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MaintenanceBlockResponseDTO> createBlock(
            @Valid @RequestBody MaintenanceBlockRequestDTO request) {
        MaintenanceBlockResponseDTO response = maintenanceService.createBlock(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lifts a maintenance block and returns the freed capacity to the allotment.
     *
     * <p><strong>Admin only.</strong>
     *
     * @param id the identifier of the maintenance block to remove.
     * @return {@code 204 No Content} on success.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         block has that id (mapped to {@code 404}).
     * @throws org.springframework.security.access.AccessDeniedException if the caller
     *         is authenticated but not an admin (mapped to {@code 403}); an
     *         unauthenticated caller is instead rejected with {@code 401} beforehand.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeBlock(@PathVariable Long id) {
        maintenanceService.removeBlock(id);
        return ResponseEntity.noContent().build();
    }
}
