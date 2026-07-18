package com.Abdelwahab.RoomBooking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for attaching an add-on to a reservation, deserialized from the JSON
 * body of {@code POST /api/reservations/{id}/addons}. It is the wire contract for
 * an add-on line; the JPA entity is never bound directly. The target reservation
 * comes from the path, and the add-on's unit price is frozen server-side from the
 * catalogue at attach time — deliberately never accepted from the client.
 *
 * <p><strong>Validation intent.</strong> Violations are reported as
 * {@code 400 Bad Request} via {@code MethodArgumentNotValidException}.
 *
 * @param addonId the catalogue add-on to attach; {@code @NotNull}.
 * @param quantity how many units to attach; {@code @Min(1)} — attaching zero (or
 *        fewer) units is meaningless and is rejected.
 */
public record ReservationAddonRequestDTO(

    @NotNull(message = "Add-on ID is required")
    Long addonId,

    @Min(value = 1, message = "Quantity must be at least 1")
    int quantity

) {}
