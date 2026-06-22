package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for the POST /api/reservations endpoint.
 * The authenticated guest is identified from the JWT token — no guestId needed in the body.
 * Room assignment is handled automatically — guests do not pick a specific room.
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
