package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for {@code POST /api/reservations}, deserialized from the JSON body:
 * the desired stay a guest wants to book. It is the wire contract for creating a
 * reservation; the {@code Reservation} JPA entity is never bound directly.
 *
 * <p>The booking guest is resolved from the JWT, so no {@code guestId} appears in
 * the body, and a physical room is assigned automatically at check-in — guests do
 * not choose a specific room, only a rate plan.
 *
 * <p><strong>Validation intent.</strong> Field constraints are enforced by
 * {@code @Valid}, and the compact constructor adds a cross-field invariant; both
 * surface as {@code 400 Bad Request} (bean-validation failures via
 * {@code MethodArgumentNotValidException}, the invariant via the
 * {@code IllegalArgumentException} handler).
 *
 * @param ratePlanId the chosen rate plan (which resolves the room type and
 *        pricing); {@code @NotNull}.
 * @param checkInDate the arrival day; {@code @NotNull} and
 *        {@code @FutureOrPresent} — today or later, since a past stay cannot be
 *        booked.
 * @param checkOutDate the departure day; {@code @NotNull} and {@code @Future} —
 *        strictly after today, guaranteeing at least one night.
 * @param numGuests the party size; {@code @Min(1)} — at least one guest is
 *        required.
 * @throws IllegalArgumentException if {@code checkOutDate} is not strictly after
 *         {@code checkInDate} (a zero- or negative-length stay), thrown from the
 *         compact constructor and mapped to {@code 400}.
 */
public record ReservationRequestDTO(

    @NotNull(message = "Rate plan ID cannot be empty")
    Long ratePlanId,

    @NotNull(message = "Check-in date cannot be empty")
    @FutureOrPresent(message = "Check-in date cannot be in the past")
    LocalDate checkInDate,

    @NotNull(message = "Check-out date cannot be empty")
    @Future(message = "Check-out date must be in the future")
    LocalDate checkOutDate,

    @Min(value = 1, message = "At least 1 guest is required")
    int numGuests

) {
    public ReservationRequestDTO {
        if (checkInDate != null && checkOutDate != null && !checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }
    }
}
