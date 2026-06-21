package com.Abdelwahab.RoomBooking.dto;

import java.util.List;

/**
 * Response DTO representing one available room type in the search results.
 * Groups the room type with all its active rate plans so the guest can
 * compare prices before confirming their booking.
 */
public record AvailabilityResponseDTO(
    Long roomTypeId,
    String roomTypeName,
    String description,
    int maxOccupancy,
    int availableRoomsCount,
    double basePricePerNight,
    List<RatePlanResponseDTO> ratePlans
) {}
