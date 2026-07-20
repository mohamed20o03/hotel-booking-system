package com.Abdelwahab.RoomBooking.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Abdelwahab.RoomBooking.dto.AvailabilityResponseDTO;
import com.Abdelwahab.RoomBooking.service.ReservationService;

import lombok.RequiredArgsConstructor;

/**
 * HTTP entry point for availability search: finding bookable room types and their
 * priced rate plans for a hotel over a date range.
 *
 * <p><strong>Architectural role.</strong> A thin web-contract adapter. It binds the
 * query parameters, parses the dates, delegates to {@link ReservationService}, and
 * maps the result to an HTTP status code. It holds no business logic: the inventory
 * calendar, occupancy filtering, and day-by-day pricing live in the service layer.
 *
 * <p><strong>Thread safety.</strong> Stateless and therefore thread-safe. Its only
 * field is the injected singleton {@link ReservationService}; each request runs on
 * its own thread with request-scoped arguments.
 *
 * <p><strong>Security &amp; scope.</strong> The endpoint is a {@code GET} under
 * {@code /api/hotels/**}, so it is <strong>public</strong> per the
 * {@code GET /api/hotels/**} {@code permitAll} rule in {@code SecurityConfig}:
 * browsing availability needs no login.
 *
 * <p><strong>Error contract.</strong> This endpoint takes no {@code @Valid} body and
 * throws no domain exceptions of its own; the underlying read is tolerant of an
 * unknown hotel (returning an empty list). A malformed {@code checkIn}/{@code checkOut}
 * value raises a {@code DateTimeParseException} during parsing, which
 * {@code GlobalExceptionHandler} maps to {@code 400 Bad Request}.
 *
 * @see ReservationService
 * @see com.Abdelwahab.RoomBooking.exception.GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class SearchController {

    private final ReservationService reservationService;

    /**
     * Searches a hotel for room types that still have allotment across the requested
     * stay and can seat the party, each paired with its rate plans priced for those
     * exact dates.
     *
     * <p>Public; no authentication required. Room types whose maximum occupancy is
     * below {@code guests}, or that are sold out on any night of the range, are
     * omitted from the result.
     *
     * @param hotelId  the hotel to search.
     * @param checkIn  the arrival date, an ISO-8601 {@code yyyy-MM-dd} string
     *                (e.g. {@code "2026-07-01"}).
     * @param checkOut the departure date, an ISO-8601 {@code yyyy-MM-dd} string,
     *                exclusive of the last night.
     * @param guests   the party size the room type must accommodate.
     * @return {@code 200 OK} with the matching {@link AvailabilityResponseDTO}s; an
     *         empty list if nothing is bookable for the criteria (including an unknown
     *         hotel).
     * @throws java.time.format.DateTimeParseException if {@code checkIn} or
     *         {@code checkOut} is not a valid ISO-8601 date (mapped to {@code 400}).
     */
    @GetMapping("/{hotelId}/availability")
    public ResponseEntity<List<AvailabilityResponseDTO>> searchAvailability(
            @PathVariable Long hotelId,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestParam int guests) {

        LocalDate checkInDate = LocalDate.parse(checkIn);
        LocalDate checkOutDate = LocalDate.parse(checkOut);

        List<AvailabilityResponseDTO> results = reservationService.searchAvailableOptions(
                hotelId, checkInDate, checkOutDate, guests);

        return ResponseEntity.ok(results);
    }
}
