package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for the search-availability endpoint
 * ({@code GET /api/hotels/{hotelId}/availability}), bound from the query
 * parameters. It is the wire contract describing what the guest wants to search
 * for; the response is a list of {@link AvailabilityResponseDTO}, never entities.
 *
 * <p><strong>Validation intent.</strong> Field constraints are enforced by
 * {@code @Valid}, and the compact constructor adds a cross-field invariant; both
 * surface as {@code 400 Bad Request} (bean-validation failures via
 * {@code MethodArgumentNotValidException}, the invariant via the
 * {@code IllegalArgumentException} handler).
 *
 * @param hotelId the hotel to search within; {@code @NotNull}.
 * @param checkInDate the desired arrival day; {@code @NotNull} and
 *        {@code @FutureOrPresent} — today or later, since a past stay cannot be
 *        booked.
 * @param checkOutDate the desired departure day; {@code @NotNull} and
 *        {@code @Future} — strictly after today, guaranteeing at least one night.
 * @param numGuests the party size to accommodate; {@code @Min(1)} — at least one
 *        guest is required.
 * @throws IllegalArgumentException if {@code checkOutDate} is not strictly after
 *         {@code checkInDate} (a zero- or negative-length stay), thrown from the
 *         compact constructor and mapped to {@code 400}.
 */
public record SearchRequestDTO(

    @NotNull(message = "Hotel ID cannot be empty")
    Long hotelId,

    @NotNull(message = "Check-in date cannot be empty")
    @FutureOrPresent(message = "Check-in date cannot be in the past")
    LocalDate checkInDate,

    @NotNull(message = "Check-out date cannot be empty")
    @Future(message = "Check-out date must be in the future")
    LocalDate checkOutDate,

    @Min(value = 1, message = "At least 1 guest is required")
    int numGuests

) {
    public SearchRequestDTO {
        if (checkInDate != null && checkOutDate != null && !checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }
    }
}
