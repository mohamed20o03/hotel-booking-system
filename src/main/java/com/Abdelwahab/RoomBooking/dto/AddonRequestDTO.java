package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating/updating an add-on in a hotel's catalogue (admin only).
 * The owning hotel comes from the path, not the body.
 */
public record AddonRequestDTO(

    @NotBlank(message = "Add-on name is required")
    String name,

    @NotBlank(message = "Category is required") // e.g. TRANSPORTATION, FOOD, SPA
    String category,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price cannot be negative")
    BigDecimal price,

    @NotBlank(message = "Price unit is required") // e.g. PER_PERSON, PER_NIGHT, FLAT_RATE
    String priceUnit,

    // Optional — defaults to true (available) when omitted.
    Boolean available

) {}
