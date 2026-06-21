package com.Abdelwahab.RoomBooking.dto;

public record RoomTypeResponseDTO(
    Long id,
    String name,
    String description,
    int maxOccupancy,
    int totalRooms,
    double basePricePerNight
) {}
