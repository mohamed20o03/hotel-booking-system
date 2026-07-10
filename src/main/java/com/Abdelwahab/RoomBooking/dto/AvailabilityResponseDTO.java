package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO representing one available room type in the search results.
 * Groups the room type with all its rate plans, each already priced for the
 * requested dates so the guest can compare real totals before booking.
 */
public record AvailabilityResponseDTO(
    Long roomTypeId,
    String roomTypeName,
    String description,
    int maxOccupancy,
    int availableRoomsCount,
    BigDecimal basePricePerNight,
    String currency,
    List<RatePlanResponseDTO> ratePlans
) {}
