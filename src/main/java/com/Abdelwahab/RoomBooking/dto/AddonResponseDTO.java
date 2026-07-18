package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

/**
 * Response DTO for an add-on in a hotel's catalogue, serialized to the JSON body
 * of the endpoints under {@code /api/hotels/{hotelId}/addons}. It is the outbound
 * wire shape for a catalogue add-on; the {@code Addon} JPA entity is never
 * serialized directly.
 *
 * @param id the add-on's stable identifier.
 * @param hotelId the owning hotel.
 * @param name the display name.
 * @param category the classification bucket (e.g. {@code TRANSPORTATION}).
 * @param price the catalogue unit price.
 * @param priceUnit how the price applies (e.g. {@code PER_PERSON},
 *        {@code PER_NIGHT}, {@code FLAT_RATE}).
 * @param available whether the add-on is currently offered.
 */
public record AddonResponseDTO(
    Long id,
    Long hotelId,
    String name,
    String category,
    BigDecimal price,
    String priceUnit,
    boolean available
) {}
