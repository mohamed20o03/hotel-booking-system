package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;

/**
 * A price option (Rate Plan) shown to the guest during search.
 */
public record RatePlanDTO(
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
