package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for putting a physical room under maintenance for a date range.
 * The block covers the half-open interval [startDate, endDate) — the end date
 * itself is the day the room comes back into service and is not blocked.
 */
public record MaintenanceBlockRequestDTO(

    @NotNull(message = "Room ID cannot be empty")
    Long roomId,

    @NotNull(message = "Start date cannot be empty")
    @FutureOrPresent(message = "Start date cannot be in the past")
    LocalDate startDate,

    @NotNull(message = "End date cannot be empty")
    @FutureOrPresent(message = "End date cannot be in the past")
    LocalDate endDate,

    String reason

) {
    public MaintenanceBlockRequestDTO {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
    }
}
