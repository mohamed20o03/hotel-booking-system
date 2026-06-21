package com.Abdelwahab.RoomBooking.dto;

public record HotelResponseDTO(
    Long id,
    String name,
    String city,
    String country,
    Integer starRating,
    String phone,
    String email,
    String timezone
) {}
