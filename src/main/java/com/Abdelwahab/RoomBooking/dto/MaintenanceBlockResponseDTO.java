package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;

/**
 * Response DTO returned after a maintenance block is created.
 */
public record MaintenanceBlockResponseDTO(
    Long id,
    Long roomId,
    int roomNumber,
    String roomTypeName,
    LocalDate startDate,
    LocalDate endDate,
    String reason
) {}
