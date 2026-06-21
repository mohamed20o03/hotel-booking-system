package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDateTime;

public record GuestResponseDTO (
    Long id,
    String firstName, 
    String lastName,
    String email, 
    String phone, 
    String loyaltyTier, 
    LocalDateTime createdAt
) {}
