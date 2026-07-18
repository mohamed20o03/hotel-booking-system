package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for putting a physical room under maintenance over a date range,
 * deserialized from the JSON body of {@code POST /api/maintenance} (admin-only).
 * It is the wire contract for a maintenance block; the JPA entity is never bound
 * directly. The block covers the half-open interval {@code [startDate, endDate)} —
 * the end date itself is the day the room returns to service and is not blocked.
 *
 * <p><strong>Validation intent.</strong> Field constraints are enforced by
 * {@code @Valid}, and the compact constructor adds a cross-field invariant; both
 * surface as {@code 400 Bad Request} (bean-validation failures via
 * {@code MethodArgumentNotValidException}, the invariant via the
 * {@code IllegalArgumentException} handler).
 *
 * @param roomId the physical room to block; {@code @NotNull}.
 * @param startDate the first blocked day; {@code @NotNull} and
 *        {@code @FutureOrPresent} — a room cannot be blocked in the past.
 * @param endDate the exclusive end (day the room returns); {@code @NotNull} and
 *        {@code @FutureOrPresent}.
 * @param reason optional free-text note on why the room is out of service;
 *        nullable, no constraint.
 * @throws IllegalArgumentException if {@code endDate} is not strictly after
 *         {@code startDate} (a zero- or negative-length block), thrown from the
 *         compact constructor and mapped to {@code 400}.
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
