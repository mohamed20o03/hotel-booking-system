package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

/**
 * Response DTO for an add-on attached to a reservation. unitPrice is the price
 * frozen at attach time; lineTotal is quantity * unitPrice (computed, never stored).
 */
public record ReservationAddonResponseDTO(
    Long id,
    Long addonId,
    String name,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {}
