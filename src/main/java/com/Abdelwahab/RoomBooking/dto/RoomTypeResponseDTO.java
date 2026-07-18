package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

/**
 * Response DTO exposing a room type's details, serialized to the JSON body of the
 * read endpoint {@code GET /api/hotels/{hotelId}/room-types} (and returned by the
 * create/update endpoints). It is the outbound wire shape for a room type; the
 * {@code RoomType} JPA entity is never serialized directly.
 *
 * @param id the room type's stable identifier.
 * @param name the display name (e.g. {@code "Deluxe Double"}).
 * @param description free-text marketing copy, if any.
 * @param maxOccupancy the most guests the type sleeps.
 * @param totalRooms the physical inventory count of this type.
 * @param basePricePerNight the fallback nightly rate before any rate-plan
 *        override.
 * @param currency the ISO 4217 code the price is quoted in.
 */
public record RoomTypeResponseDTO(
    Long id,
    String name,
    String description,
    int maxOccupancy,
    int totalRooms,
    BigDecimal basePricePerNight,
    String currency
) {}
