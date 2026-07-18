package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a single night within a stay's price breakdown, nested (as a
 * list) inside {@link ReservationConfirmationDTO}. It is a read-only wire shape
 * explaining how the total was derived, night by night; no entity is serialized
 * directly.
 *
 * @param date the calendar night this line prices.
 * @param amount the charge for that night.
 * @param source how the amount was determined — {@code "PLAN"} when a rate-plan
 *        override applied, or {@code "BASE"} when the room type's base rate was
 *        used.
 */
public record NightlyPriceDTO(
    LocalDate date,
    BigDecimal amount,
    String source
) {}
