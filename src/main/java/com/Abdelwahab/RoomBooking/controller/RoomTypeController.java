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

import com.Abdelwahab.RoomBooking.dto.RoomTypeRequestDTO;
import com.Abdelwahab.RoomBooking.dto.RoomTypeResponseDTO;
import com.Abdelwahab.RoomBooking.service.RoomTypeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * HTTP entry point for a hotel's room-type catalogue: browsing room types and, for
 * staff, creating, updating, and removing them.
 *
 * <p><strong>Architectural role.</strong> A thin web-contract adapter. It binds and
 * validates requests, delegates to {@link RoomTypeService}, and maps results to HTTP
 * status codes. It holds no business logic: persistence and the check that a room
 * type belongs to the hotel in the path live in the service layer.
 *
 * <p><strong>Thread safety.</strong> Stateless and therefore thread-safe. Its only
 * field is the injected singleton {@link RoomTypeService}; each request runs on its
 * own thread with request-scoped arguments.
 *
 * <p><strong>Security &amp; scope.</strong> The path is nested under
 * {@code /api/hotels/**}, so enforcement is layered:
 * <ul>
 *   <li><strong>Public</strong> — {@link #getRoomTypesByHotel(Long)} needs no login,
 *       per the {@code GET /api/hotels/**} {@code permitAll} rule in
 *       {@code SecurityConfig}.</li>
 *   <li><strong>Admin only</strong> — {@code POST}/{@code PUT}/{@code DELETE} are
 *       gated both by the verb-specific {@code /api/hotels/**} URL rules in
 *       {@code SecurityConfig} and by {@code @PreAuthorize("hasRole('ADMIN')")} on
 *       each method, so the guard survives a URL-rule refactor.</li>
 * </ul>
 * Every write is scoped to the hotel in the path, so one hotel's room type can never
 * be mutated through another hotel's URL. An unauthenticated request to a protected
 * verb yields {@code 401 Unauthorized} (via {@code RestAuthenticationEntryPoint}),
 * whereas an authenticated non-admin request yields {@code 403 Forbidden}.
 *
 * <p><strong>Error contract.</strong> Domain exceptions are mapped centrally by
 * {@code GlobalExceptionHandler}: {@code ResourceNotFoundException → 404} (unknown
 * hotel or room type, or a room type that belongs to a different hotel),
 * bean-validation failures on {@code @Valid → 400}, authentication failures
 * {@code → 401}, and authorization failures {@code → 403}.
 *
 * @see RoomTypeService
 * @see com.Abdelwahab.RoomBooking.exception.GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/hotels/{hotelId}/room-types")
@RequiredArgsConstructor
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    /**
     * Lists every room type offered by the given hotel.
     *
     * <p>Public; no authentication required.
     *
     * @param hotelId the hotel whose room types are requested.
     * @return {@code 200 OK} with the hotel's {@link RoomTypeResponseDTO}s; an empty
     *         list if it defines none.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         hotel has that id (mapped to {@code 404}).
     */
    @GetMapping
    public ResponseEntity<List<RoomTypeResponseDTO>> getRoomTypesByHotel(@PathVariable Long hotelId) {
        List<RoomTypeResponseDTO> roomTypes = roomTypeService.getRoomTypesByHotel(hotelId);
        return ResponseEntity.ok(roomTypes);
    }

    /**
     * Adds a new room type to a hotel's catalogue.
     *
     * <p><strong>Admin only.</strong> The request body is validated with
     * {@code @Valid} before the service is reached.
     *
     * @param hotelId the hotel that will own the new room type.
     * @param request the room type to create — name, description, max occupancy,
     *                total rooms, base nightly price, and currency; validated by DTO
     *                constraints.
     * @return {@code 201 Created} with the persisted {@link RoomTypeResponseDTO},
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
    public ResponseEntity<RoomTypeResponseDTO> createRoomType(
            @PathVariable Long hotelId,
            @Valid @RequestBody RoomTypeRequestDTO request) {
        RoomTypeResponseDTO created = roomTypeService.createRoomType(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Replaces the mutable fields of an existing room type.
     *
     * <p><strong>Admin only.</strong> The service asserts the room type belongs to
     * the hotel in the path before applying changes. The request body is validated
     * with {@code @Valid} before the service is reached.
     *
     * @param hotelId the hotel that must own the room type.
     * @param id      the identifier of the room type to update.
     * @param request the new field values; validated by DTO constraints.
     * @return {@code 200 OK} with the updated {@link RoomTypeResponseDTO}.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         such room type exists or it belongs to a different hotel (mapped to
     *         {@code 404}).
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if the body
     *         fails bean validation (mapped to {@code 400}).
     * @throws org.springframework.security.access.AccessDeniedException if the caller
     *         is authenticated but not an admin (mapped to {@code 403}); an
     *         unauthenticated caller is instead rejected with {@code 401} beforehand.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomTypeResponseDTO> updateRoomType(
            @PathVariable Long hotelId,
            @PathVariable Long id,
            @Valid @RequestBody RoomTypeRequestDTO request) {
        RoomTypeResponseDTO updated = roomTypeService.updateRoomType(hotelId, id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Removes a room type from a hotel's catalogue.
     *
     * <p><strong>Admin only.</strong> The service asserts the room type belongs to
     * the hotel in the path before deleting.
     *
     * @param hotelId the hotel that must own the room type.
     * @param id      the identifier of the room type to delete.
     * @return {@code 204 No Content} on success.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         such room type exists or it belongs to a different hotel (mapped to
     *         {@code 404}).
     * @throws org.springframework.security.access.AccessDeniedException if the caller
     *         is authenticated but not an admin (mapped to {@code 403}); an
     *         unauthenticated caller is instead rejected with {@code 401} beforehand.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoomType(
            @PathVariable Long hotelId,
            @PathVariable Long id) {
        roomTypeService.deleteRoomType(hotelId, id);
        return ResponseEntity.noContent().build();
    }
}
