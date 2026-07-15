package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or updating a room type within a hotel (admin-only).
 *
 * The owning hotel comes from the path (/api/hotels/{hotelId}/room-types), so it
 * is not part of the body. Currency defaults to EGP at the entity level when
 * omitted, matching the schema default.
 */
public record RoomTypeRequestDTO(

    @NotBlank(message = "Room type name is required")
    String name,

    String description,

    @Min(value = 1, message = "Max occupancy must be at least 1")
    int maxOccupancy,

    @Min(value = 0, message = "Total rooms cannot be negative")
    int totalRooms,

    @NotNull(message = "Base price per night is required")
    @DecimalMin(value = "0.00", message = "Base price cannot be negative")
    BigDecimal basePricePerNight,

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
    String currency

) {}
