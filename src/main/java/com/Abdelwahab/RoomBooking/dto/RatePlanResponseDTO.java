package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

/**
 * Response DTO representing one bookable rate plan for a searched stay.
 * The price is computed day-by-day for the requested dates (plan overrides +
 * base-rate fallback), so it reflects the real total the guest would pay — not
 * a single flat nightly figure.
 */
public record RatePlanResponseDTO(
    Long ratePlanId,
    String name,
    String currency,
    int minStayNights,
    boolean breakfastIncluded,
    boolean isRefundable,
    int nights,
    BigDecimal totalPrice
) {}
