package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO returned after a successful booking.
 * Contains everything the guest needs for their confirmation page.
 */
public record BookingResponseDTO(
    Long reservationId,
    String confirmationNumber,
    String guestName,
    String roomTypeName,
    Long assignedRoomId,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int numGuests,
    double totalPrice,
    String currency,
    String status,
    LocalDateTime createdAt
) {}
