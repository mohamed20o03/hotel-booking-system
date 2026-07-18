package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

/**
 * Response DTO for an add-on line attached to a reservation, serialized to the
 * JSON body of the add-on endpoints under {@code /api/reservations/{id}/addons}.
 * It is the outbound wire shape; the JPA entity is never serialized directly.
 *
 * @param id the add-on line's identifier (the value used to detach it).
 * @param addonId the catalogue add-on this line references.
 * @param name the add-on's display name.
 * @param quantity the number of units attached.
 * @param unitPrice the per-unit price frozen from the catalogue at attach time.
 * @param lineTotal the line charge ({@code quantity * unitPrice}); computed on the
 *        fly, never stored.
 */
public record ReservationAddonResponseDTO(
    Long id,
    Long addonId,
    String name,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {}
