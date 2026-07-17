package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

/**
 * Response DTO for an add-on in a hotel's catalogue.
 */
public record AddonResponseDTO(
    Long id,
    Long hotelId,
    String name,
    String category,
    BigDecimal price,
    String priceUnit,
    boolean available
) {}
