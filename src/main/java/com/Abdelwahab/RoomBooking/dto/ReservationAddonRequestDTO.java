package com.Abdelwahab.RoomBooking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for attaching an add-on to a reservation. The reservation comes
 * from the path; the add-on's unit price is frozen server-side from the catalogue
 * at attach time, never taken from the client.
 */
public record ReservationAddonRequestDTO(

    @NotNull(message = "Add-on ID is required")
    Long addonId,

    @Min(value = 1, message = "Quantity must be at least 1")
    int quantity

) {}
