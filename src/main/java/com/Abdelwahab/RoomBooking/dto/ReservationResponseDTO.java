package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for looking up an existing reservation.
 * Used by the GET /api/reservations/{confirmationNumber}
 * and GET /api/reservations/my-reservations endpoints.
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
    String currency,

    // Stay info
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int numGuests,
    int nights,
    BigDecimal totalPrice,

    LocalDateTime createdAt
) {}
