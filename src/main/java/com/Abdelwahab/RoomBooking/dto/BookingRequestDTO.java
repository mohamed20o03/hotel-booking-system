package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BookingRequestDTO(
    @NotNull(message = "RoomId cannot be empty.")
    Long roomId,

    @NotNull(message = "UserId cannot be empty.")
    Long userId,

    @NotNull(message = "Start Date Cannot be empty.")
    @FutureOrPresent(message = "Start date cannot be in the past")
    LocalDateTime startDate,

    @Future(message = "End date must be in the future")
    LocalDateTime endDate,

    @Min(value = 1, message = "At least one person must attend")
    int numOfAttendance
) {
    public BookingRequestDTO {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
    }
}
