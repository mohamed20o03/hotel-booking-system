package com.Abdelwahab.RoomBooking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating or updating an add-on in a hotel's catalogue,
 * deserialized from the JSON body of the admin-only endpoints under
 * {@code /api/hotels/{hotelId}/addons}. It is the wire contract for catalogue
 * mutations; the {@code Addon} JPA entity is never bound directly. The owning
 * hotel is taken from the path, not this body.
 *
 * <p><strong>Validation intent.</strong> Violations are reported as
 * {@code 400 Bad Request} via {@code MethodArgumentNotValidException}.
 *
 * @param name the add-on's display name; {@code @NotBlank}.
 * @param category the classification bucket (e.g. {@code TRANSPORTATION},
 *        {@code FOOD}, {@code SPA}); {@code @NotBlank}.
 * @param price the catalogue unit price; {@code @NotNull} with
 *        {@code @DecimalMin("0.00")} — zero is allowed (a complimentary extra) but
 *        a negative price is rejected.
 * @param priceUnit how the price is applied when attached to a reservation (e.g.
 *        {@code PER_PERSON}, {@code PER_NIGHT}, {@code FLAT_RATE});
 *        {@code @NotBlank}.
 * @param available whether the add-on is currently offered; optional and
 *        nullable, defaulting to {@code true} (available) when omitted.
 */
public record AddonRequestDTO(

    @NotBlank(message = "Add-on name is required")
    String name,

    @NotBlank(message = "Category is required") // e.g. TRANSPORTATION, FOOD, SPA
    String category,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price cannot be negative")
    BigDecimal price,

    @NotBlank(message = "Price unit is required") // e.g. PER_PERSON, PER_NIGHT, FLAT_RATE
    String priceUnit,

    // Optional — defaults to true (available) when omitted.
    Boolean available

) {}
