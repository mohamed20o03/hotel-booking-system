package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDateTime;

public record BookingResponseDTO(
    Long bookingId,
    Long roomId,
    Long bookingBy,
    LocalDateTime startDate,
    LocalDateTime endDate,
    String status
) {}
