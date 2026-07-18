package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO representing one available room type in the search results,
 * serialized (as a list) into the JSON body of
 * {@code GET /api/hotels/{hotelId}/availability}. It is the outbound wire shape
 * assembled from queried entities; no JPA entity is serialized directly. Each
 * element groups a room type with all its bookable rate plans, every plan already
 * priced for the requested dates so the guest can compare real totals before
 * booking.
 *
 * @param roomTypeId the room type's identifier, carried so the client can drill
 *        in without a second lookup.
 * @param roomTypeName the room type's display name.
 * @param description free-text marketing copy, if any.
 * @param maxOccupancy the most guests the type sleeps.
 * @param availableRoomsCount how many rooms of this type remain bookable for the
 *        searched range.
 * @param basePricePerNight the type's fallback nightly rate, shown for reference.
 * @param currency the ISO 4217 code the prices are quoted in.
 * @param ratePlans the bookable {@link RatePlanResponseDTO} options, each priced
 *        for the requested stay.
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
