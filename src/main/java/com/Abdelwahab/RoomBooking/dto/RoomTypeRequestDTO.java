package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or updating a room type within a hotel, deserialized
 * from the JSON body of the admin-only endpoints under
 * {@code /api/hotels/{hotelId}/room-types} ({@code POST} and {@code PUT /{id}}).
 * It is the wire contract for room-type mutations; the {@code RoomType} JPA
 * entity is never bound directly.
 *
 * <p>The owning hotel is taken from the path, so it is deliberately absent from
 * the body. {@code currency} defaults to {@code EGP} at the entity level when
 * omitted, matching the schema default.
 *
 * <p><strong>Validation intent.</strong> Violations are surfaced as
 * {@code 400 Bad Request} via {@code MethodArgumentNotValidException}.
 *
 * @param name the room type's display name (e.g. {@code "Deluxe Double"});
 *        {@code @NotBlank}.
 * @param description free-text marketing copy; optional and nullable, no
 *        constraint.
 * @param maxOccupancy the most guests the type sleeps; {@code @Min(1)} — a room
 *        that holds no one is invalid. Later cross-checked against a booking's
 *        guest count.
 * @param totalRooms the physical inventory count of this type; {@code @Min(0)} —
 *        may be zero (temporarily unstocked) but never negative.
 * @param basePricePerNight the fallback nightly rate before any rate-plan
 *        override; {@code @NotNull} with {@code @DecimalMin("0.00")} forbidding a
 *        negative price.
 * @param currency the ISO 4217 code the price is quoted in; {@code @Size(min=3,
 *        max=3)} enforcing exactly a three-letter code when supplied.
 */
public record RoomTypeRequestDTO(

    @NotBlank(message = "Room type name is required")
    String name,

    String description,

    @Min(value = 1, message = "Max occupancy must be at least 1")
    int maxOccupancy,

    @Min(value = 0, message = "Total rooms cannot be negative")
    int totalRooms,

    @NotNull(message = "Base price per night is required")
    @DecimalMin(value = "0.00", message = "Base price cannot be negative")
    BigDecimal basePricePerNight,

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
    String currency

) {}
