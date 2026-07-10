package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One night of a stay in a price breakdown, as shown to the guest.
 * source is "PLAN" (a rate plan override applied) or "BASE" (base rate used).
 */
public record NightlyPriceDTO(
    LocalDate date,
    BigDecimal amount,
    String source
) {}
