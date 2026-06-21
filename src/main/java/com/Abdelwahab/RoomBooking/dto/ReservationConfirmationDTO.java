package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO returned immediately after a successful reservation is created.
 * Contains everything the guest needs for their confirmation page or email.
 * Different from ReservationResponseDTO which is used for looking up existing reservations.
 */
public record ReservationConfirmationDTO(
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
