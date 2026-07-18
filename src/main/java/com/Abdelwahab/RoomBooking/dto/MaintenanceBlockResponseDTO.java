package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;

/**
 * Response DTO returned after a maintenance block is created, serialized to the
 * JSON body of {@code POST /api/maintenance}. It is the outbound wire shape for a
 * block; the JPA entity is never serialized directly, and it flattens the room
 * relation into denormalized display fields ({@code roomNumber},
 * {@code roomTypeName}) so the caller needs no follow-up lookup.
 *
 * @param id the block's stable identifier.
 * @param roomId the blocked physical room.
 * @param roomNumber the room's human-facing number.
 * @param roomTypeName the room type's display name.
 * @param startDate the first blocked day.
 * @param endDate the exclusive end (day the room returns to service).
 * @param reason the free-text note on why the room is out of service, if any.
 */
public record MaintenanceBlockResponseDTO(
    Long id,
    Long roomId,
    int roomNumber,
    String roomTypeName,
    LocalDate startDate,
    LocalDate endDate,
    String reason
) {}
