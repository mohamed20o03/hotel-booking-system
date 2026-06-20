package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One entry in the search results.
 * Groups a RoomType with its available Rate Plans so the guest can
 * compare prices before choosing.
 */
public record RoomTypeAvailabilityDTO(
    Long roomTypeId,
    String roomTypeName,
    String description,
    int maxOccupancy,
    int availableRoomsCount,
    BigDecimal basePricePerNight,
    List<RatePlanDTO> ratePlans
) {}
