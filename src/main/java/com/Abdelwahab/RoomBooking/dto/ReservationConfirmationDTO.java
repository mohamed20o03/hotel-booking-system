package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO returned immediately after a successful reservation is created.
 * Contains everything the guest needs for their confirmation page or email,
 * including the day-by-day price breakdown that produced the total.
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
    int nights,
    BigDecimal totalPrice,
    String currency,
    String status,
    List<NightlyPriceDTO> nightlyBreakdown,
    LocalDateTime createdAt
) {}
