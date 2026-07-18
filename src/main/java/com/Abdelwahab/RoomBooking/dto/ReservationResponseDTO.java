package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for looking up an existing reservation, serialized to the JSON body
 * of {@code GET /api/reservations/{confirmationNumber}} (single) and
 * {@code GET /api/reservations/my-reservations} (list), and returned by
 * {@code PATCH /api/reservations/{id}/check-in}. It is the outbound wire shape;
 * raw JPA entities are never exposed to the client. Related entities are
 * flattened into denormalized display fields (guest, room type, rate plan) so the
 * caller needs no follow-up lookups.
 *
 * @param reservationId the reservation's identifier.
 * @param confirmationNumber the human-facing confirmation code.
 * @param status the reservation's current state.
 * @param guestId the owning guest's identifier.
 * @param guestName the owning guest's display name.
 * @param roomTypeName the reserved room type (derived via rate plan to room type).
 * @param assignedRoomId the physical room assigned, or null if not yet assigned.
 * @param ratePlanName the booked rate plan's display name.
 * @param currency the ISO 4217 code the total is quoted in.
 * @param checkInDate the arrival day.
 * @param checkOutDate the departure day.
 * @param numGuests the party size.
 * @param nights the number of nights in the stay.
 * @param totalPrice the computed price for the whole stay.
 * @param createdAt when the reservation was created.
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
