package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;

/**
 * Response DTO representing a single rate plan option shown during availability search.
 */
public record RatePlanResponseDTO(
    Long ratePlanId,
    String name,
    double pricePerNight,
    String currency,
    LocalDate validFrom,
    LocalDate validTo,
    int minStayNights,
    boolean breakfastIncluded,
    boolean isRefundable
) {}
