package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

public record RoomTypeResponseDTO(
    Long id,
    String name,
    String description,
    int maxOccupancy,
    int totalRooms,
    BigDecimal basePricePerNight,
    String currency
) {}
