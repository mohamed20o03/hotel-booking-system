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
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP entry point for a hotel's add-on catalogue (airport transfer, spa, etc.):
 * browsing available add-ons and, for staff, creating, updating, and removing them.
 *
 * <p><strong>Architectural role.</strong> A thin web-contract adapter. It binds and
 * validates requests, delegates to {@link AddonService}, and maps results to HTTP
 * status codes. It holds no business logic: persistence and the check that an add-on
 * belongs to the hotel in the path live in the service layer.
 *
 * <p><strong>Thread safety.</strong> Stateless and therefore thread-safe. Its only
 * field is the injected singleton {@link AddonService}; each request runs on its own
 * thread with request-scoped arguments.
 *
 * <p><strong>Security &amp; scope.</strong> The path is nested under
 * {@code /api/hotels/**}, so enforcement is layered:
 * <ul>
 *   <li><strong>Public</strong> — {@link #getAvailableAddons(Long)} needs no login,
 *       per the {@code GET /api/hotels/**} {@code permitAll} rule in
 *       {@code SecurityConfig}.</li>
 *   <li><strong>Admin only</strong> — {@code POST}/{@code PUT}/{@code DELETE} are
 *       gated both by the verb-specific {@code /api/hotels/**} URL rules in
 *       {@code SecurityConfig} and by {@code @PreAuthorize("hasRole('ADMIN')")} on
 *       each method, so the guard survives a URL-rule refactor.</li>
 * </ul>
 * Every write is scoped to the hotel in the path, so one hotel's add-on can never be
 * mutated through another hotel's URL. An unauthenticated request to a protected verb
 * yields {@code 401 Unauthorized} (via {@code RestAuthenticationEntryPoint}), whereas
 * an authenticated non-admin request yields {@code 403 Forbidden}.
 *
 * <p><strong>Error contract.</strong> Domain exceptions are mapped centrally by
 * {@code GlobalExceptionHandler}: {@code ResourceNotFoundException → 404} (unknown
 * hotel or add-on, or an add-on that belongs to a different hotel), bean-validation
 * failures on {@code @Valid → 400}, authentication failures {@code → 401}, and
 * authorization failures {@code → 403}.
 *
 * @see AddonService
 * @see com.Abdelwahab.RoomBooking.exception.GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/hotels/{hotelId}/addons")
@RequiredArgsConstructor
@Slf4j
public class AddonController {

    private final AddonService addonService;

    /**
     * Lists the add-ons a guest can currently book at the given hotel (available
     * ones only).
     *
     * <p>Public; no authentication required.
     *
     * @param hotelId the hotel whose add-on catalogue is requested.
     * @return {@code 200 OK} with the available {@link AddonResponseDTO}s; an empty
     *         list if the hotel offers none.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         hotel has that id (mapped to {@code 404}).
     */
    @GetMapping
    public ResponseEntity<List<AddonResponseDTO>> getAvailableAddons(@PathVariable Long hotelId) {
        return ResponseEntity.ok(addonService.getAvailableAddons(hotelId));
    }

    /**
     * Adds a new add-on to a hotel's catalogue.
     *
     * <p><strong>Admin only.</strong> The request body is validated with
     * {@code @Valid} before the service is reached. When {@code available} is
     * omitted the add-on defaults to available.
     *
     * @param hotelId the hotel that will own the new add-on.
     * @param request the add-on to create — name, category, price, price unit, and
     *                optional availability flag; validated by DTO constraints.
     * @return {@code 201 Created} with the persisted {@link AddonResponseDTO},
     *         including its generated id.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         hotel has that id (mapped to {@code 404}).
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if the body
     *         fails bean validation (mapped to {@code 400}).
     * @throws org.springframework.security.access.AccessDeniedException if the caller
     *         is authenticated but not an admin (mapped to {@code 403}); an
     *         unauthenticated caller is instead rejected with {@code 401} beforehand.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AddonResponseDTO> createAddon(
            @PathVariable Long hotelId, @Valid @RequestBody AddonRequestDTO request) {
        log.debug("POST /api/hotels/{}/addons [name={}]", hotelId, request.name());
        AddonResponseDTO created = addonService.createAddon(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Replaces the mutable fields of an existing add-on.
     *
     * <p><strong>Admin only.</strong> The service asserts the add-on belongs to the
     * hotel in the path before applying changes. The request body is validated with
     * {@code @Valid} before the service is reached.
     *
     * @param hotelId the hotel that must own the add-on.
     * @param addonId the identifier of the add-on to update.
     * @param request the new field values; validated by DTO constraints.
     * @return {@code 200 OK} with the updated {@link AddonResponseDTO}.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         such add-on exists or it belongs to a different hotel (mapped to
     *         {@code 404}).
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if the body
     *         fails bean validation (mapped to {@code 400}).
     * @throws org.springframework.security.access.AccessDeniedException if the caller
     *         is authenticated but not an admin (mapped to {@code 403}); an
     *         unauthenticated caller is instead rejected with {@code 401} beforehand.
     */
    @PutMapping("/{addonId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AddonResponseDTO> updateAddon(
            @PathVariable Long hotelId, @PathVariable Long addonId,
            @Valid @RequestBody AddonRequestDTO request) {
        log.debug("PUT /api/hotels/{}/addons/{}", hotelId, addonId);
        return ResponseEntity.ok(addonService.updateAddon(hotelId, addonId, request));
    }

    /**
     * Removes an add-on from a hotel's catalogue.
     *
     * <p><strong>Admin only.</strong> The service asserts the add-on belongs to the
     * hotel in the path before deleting. A database {@code ON DELETE RESTRICT}
     * constraint prevents removing an add-on still referenced by a reservation.
     *
     * @param hotelId the hotel that must own the add-on.
     * @param addonId the identifier of the add-on to delete.
     * @return {@code 204 No Content} on success.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         such add-on exists or it belongs to a different hotel (mapped to
     *         {@code 404}).
     * @throws org.springframework.security.access.AccessDeniedException if the caller
     *         is authenticated but not an admin (mapped to {@code 403}); an
     *         unauthenticated caller is instead rejected with {@code 401} beforehand.
     */
    @DeleteMapping("/{addonId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAddon(
            @PathVariable Long hotelId, @PathVariable Long addonId) {
        log.debug("DELETE /api/hotels/{}/addons/{}", hotelId, addonId);
        addonService.deleteAddon(hotelId, addonId);
        return ResponseEntity.noContent().build();
    }
}
