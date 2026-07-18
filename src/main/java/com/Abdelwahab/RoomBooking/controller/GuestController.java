package com.Abdelwahab.RoomBooking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.GuestResponseDTO;
import com.Abdelwahab.RoomBooking.service.GuestService;

import lombok.RequiredArgsConstructor;

/**
 * HTTP entry point for reading guest profiles.
 *
 * <p><strong>Architectural role.</strong> A thin web-contract adapter. It binds the
 * request, delegates to {@link GuestService}, and maps the result to an HTTP status
 * code. It holds no business logic: lookup and existence checks live in the service
 * layer.
 *
 * <p><strong>Thread safety.</strong> Stateless and therefore thread-safe. Its only
 * field is the injected singleton {@link GuestService}; each request runs on its own
 * thread with request-scoped arguments.
 *
 * <p><strong>Security &amp; scope.</strong> The {@code /api/guests/**} path matches no
 * explicit rule in {@code SecurityConfig}, so it falls to the
 * {@code anyRequest().authenticated()} default: any logged-in caller, regardless of
 * role, may read a profile. Because there is no {@code AuthenticationEntryPoint}, an
 * unauthenticated request yields {@code 403 Forbidden}. Note the lookup is keyed by
 * id rather than by the caller's identity, so it is not ownership-scoped.
 *
 * <p><strong>Error contract.</strong> Domain exceptions are mapped centrally by
 * {@code GlobalExceptionHandler}: {@code ResourceNotFoundException → 404}.
 *
 * @see GuestService
 * @see com.Abdelwahab.RoomBooking.exception.GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/guests")
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;

    /**
     * Retrieves a guest profile by its identifier.
     *
     * <p>Requires authentication; any role is accepted.
     *
     * @param id the identifier of the guest to fetch.
     * @return {@code 200 OK} with the matching {@link GuestResponseDTO}.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         guest has that id (mapped to {@code 404}).
     */
    @GetMapping("/{id}")
    public ResponseEntity<GuestResponseDTO> getGuestById(@PathVariable Long id) {
        GuestResponseDTO response = guestService.getGuestById(id);
        return ResponseEntity.ok(response);
    }
}
