package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for looking up an existing reservation.
 * Used by the GET /api/reservations/{confirmationNumber}
 * and GET /api/reservations/guest/{guestId} endpoints.
 *
 * Never exposes raw JPA entities to the client.
 */
public record ReservationResponseDTO(
    Long reservationId,
    String confirmationNumber,
    String status,

    // Guest info
    Long guestId,
    String guestName,

    // Room info (derived via ratePlan -> roomType)
    String roomTypeName,
    Long assignedRoomId,

    // Rate plan info
    String ratePlanName,
    double pricePerNight,
    String currency,

    // Stay info
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int numGuests,
    double totalPrice,

    LocalDateTime createdAt
) {}
