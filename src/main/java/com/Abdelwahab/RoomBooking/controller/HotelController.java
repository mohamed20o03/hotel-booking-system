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

import com.Abdelwahab.RoomBooking.dto.HotelRequestDTO;
import com.Abdelwahab.RoomBooking.dto.HotelResponseDTO;
import com.Abdelwahab.RoomBooking.service.HotelService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * HTTP entry point for the hotel catalogue: browsing hotels and, for staff,
 * creating, updating, and removing them.
 *
 * <p><strong>Architectural role.</strong> A thin web-contract adapter. It binds and
 * validates requests, delegates to {@link HotelService}, and maps results to HTTP
 * status codes. It holds no business logic: persistence and existence checks live in
 * the service layer.
 *
 * <p><strong>Thread safety.</strong> Stateless and therefore thread-safe. Its only
 * field is the injected singleton {@link HotelService}; each request runs on its own
 * thread with request-scoped arguments.
 *
 * <p><strong>Security &amp; scope.</strong> Enforcement is layered so the guard
 * survives a URL-rule refactor:
 * <ul>
 *   <li><strong>Public</strong> — the {@code GET} reads ({@link #getAllHotels()} and
 *       {@link #getHotelById(Long)}) need no login, per the
 *       {@code GET /api/hotels/**} {@code permitAll} rule in {@code SecurityConfig}.</li>
 *   <li><strong>Admin only</strong> — {@code POST}/{@code PUT}/{@code DELETE} are
 *       gated both by the verb-specific URL rules in {@code SecurityConfig} and by
 *       {@code @PreAuthorize("hasRole('ADMIN')")} on each method.</li>
 * </ul>
 * Because there is no {@code AuthenticationEntryPoint}, an unauthenticated request to
 * a protected verb, and an authenticated non-admin request alike, both yield
 * {@code 403 Forbidden}.
 *
 * <p><strong>Error contract.</strong> Domain exceptions are mapped centrally by
 * {@code GlobalExceptionHandler}: {@code ResourceNotFoundException → 404},
 * bean-validation failures on {@code @Valid → 400}, and authorization failures
 * {@code → 403}.
 *
 * @see HotelService
 * @see com.Abdelwahab.RoomBooking.exception.GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    /**
     * Lists every hotel in the catalogue.
     *
     * <p>Public; no authentication required.
     *
     * @return {@code 200 OK} with all hotels as {@link HotelResponseDTO}s; an empty
     *         list if the catalogue is empty.
     */
    @GetMapping
    public ResponseEntity<List<HotelResponseDTO>> getAllHotels() {
        List<HotelResponseDTO> hotels = hotelService.getAllHotels();
        return ResponseEntity.ok(hotels);
    }

    /**
     * Retrieves a single hotel by its identifier.
     *
     * <p>Public; no authentication required.
     *
     * @param id the identifier of the hotel to fetch.
     * @return {@code 200 OK} with the matching {@link HotelResponseDTO}.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         hotel has that id (mapped to {@code 404}).
     */
    @GetMapping("/{id}")
    public ResponseEntity<HotelResponseDTO> getHotelById(@PathVariable Long id) {
        HotelResponseDTO hotel = hotelService.getHotelById(id);
        return ResponseEntity.ok(hotel);
    }

    /**
     * Creates a new hotel in the catalogue.
     *
     * <p><strong>Admin only.</strong> The request body is validated with
     * {@code @Valid} before the service is reached.
     *
     * @param request the hotel to create — name, address, contact details, star
     *                rating, and timezone; validated by DTO constraints.
     * @return {@code 201 Created} with the persisted {@link HotelResponseDTO},
     *         including its generated id.
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if the body
     *         fails bean validation (mapped to {@code 400}).
     * @throws org.springframework.security.access.AccessDeniedException if the caller
     *         is not an admin, or is unauthenticated (both mapped to {@code 403}).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HotelResponseDTO> createHotel(@Valid @RequestBody HotelRequestDTO request) {
        HotelResponseDTO created = hotelService.createHotel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Replaces the mutable fields of an existing hotel.
     *
     * <p><strong>Admin only.</strong> The request body is validated with
     * {@code @Valid} before the service is reached.
     *
     * @param id      the identifier of the hotel to update.
     * @param request the new field values; validated by DTO constraints.
     * @return {@code 200 OK} with the updated {@link HotelResponseDTO}.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         hotel has that id (mapped to {@code 404}).
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if the body
     *         fails bean validation (mapped to {@code 400}).
     * @throws org.springframework.security.access.AccessDeniedException if the caller
     *         is not an admin, or is unauthenticated (both mapped to {@code 403}).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HotelResponseDTO> updateHotel(
            @PathVariable Long id, @Valid @RequestBody HotelRequestDTO request) {
        return ResponseEntity.ok(hotelService.updateHotel(id, request));
    }

    /**
     * Removes a hotel from the catalogue.
     *
     * <p><strong>Admin only.</strong>
     *
     * @param id the identifier of the hotel to delete.
     * @return {@code 204 No Content} on success.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         hotel has that id (mapped to {@code 404}).
     * @throws org.springframework.security.access.AccessDeniedException if the caller
     *         is not an admin, or is unauthenticated (both mapped to {@code 403}).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHotel(@PathVariable Long id) {
        hotelService.deleteHotel(id);
        return ResponseEntity.noContent().build();
    }
}
