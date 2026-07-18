package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO returned immediately after a reservation is created, serialized to
 * the JSON body of {@code POST /api/reservations} ({@code 201 Created}). It is the
 * outbound wire shape; the {@code Reservation} JPA entity is never serialized
 * directly. It carries everything the guest needs for a confirmation page or
 * email, including the day-by-day price breakdown that produced the total.
 *
 * @param reservationId the new reservation's identifier.
 * @param confirmationNumber the human-facing confirmation code issued at booking.
 * @param guestName the booking guest's display name.
 * @param roomTypeName the reserved room type's display name.
 * @param assignedRoomId the physical room, if one has been assigned yet
 *        (typically null until check-in).
 * @param checkInDate the arrival day.
 * @param checkOutDate the departure day.
 * @param numGuests the party size.
 * @param nights the number of nights in the stay.
 * @param totalPrice the computed price for the whole stay.
 * @param currency the ISO 4217 code the total is quoted in.
 * @param status the reservation's state (e.g. {@code PENDING} until payment
 *        confirms it).
 * @param nightlyBreakdown the per-night {@link NightlyPriceDTO} lines that sum to
 *        the total.
 * @param createdAt when the reservation was created.
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
