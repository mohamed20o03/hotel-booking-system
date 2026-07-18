package com.Abdelwahab.RoomBooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating or updating a hotel, deserialized from the JSON body
 * of the admin-only write endpoints under {@code /api/hotels}
 * ({@code POST} and {@code PUT /{id}}). It is the wire contract for hotel
 * mutations; the {@code Hotel} JPA entity is never bound directly, keeping
 * generated ids and audit state off the public surface.
 *
 * <p><strong>Validation intent.</strong> Constraints mirror the schema rules on
 * the hotel table so a bad request is rejected at the edge with
 * {@code 400 Bad Request} (via {@code MethodArgumentNotValidException}) rather
 * than failing deep in the database.
 *
 * @param name the hotel's display name; {@code @NotBlank}.
 * @param address the street address; {@code @NotBlank}.
 * @param city the city the hotel sits in; {@code @NotBlank}, also used as a
 *        search facet.
 * @param country the country; {@code @NotBlank}.
 * @param phone the front-desk contact number; {@code @NotBlank}.
 * @param email the hotel contact address; {@code @NotBlank} and {@code @Email}
 *        for syntactic validity.
 * @param starRating the official quality rating; {@code @NotNull} with
 *        {@code @Min(0)}/{@code @Max(5)} bounding it to the 0–5 star scale — any
 *        value outside that range is a violation.
 * @param timezone optional IANA zone id (e.g. {@code "Africa/Cairo"}); unvalidated
 *        and nullable, used to interpret local check-in/out semantics.
 */
public record HotelRequestDTO(

    @NotBlank(message = "Hotel name is required")
    String name,

    @NotBlank(message = "Address is required")
    String address,

    @NotBlank(message = "City is required")
    String city,

    @NotBlank(message = "Country is required")
    String country,

    @NotBlank(message = "Phone is required")
    String phone,

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    String email,

    @NotNull(message = "Star rating is required")
    @Min(value = 0, message = "Star rating cannot be below 0")
    @Max(value = 5, message = "Star rating cannot exceed 5")
    Integer starRating,

    // Optional IANA zone id, e.g. "Africa/Cairo"
    String timezone

) {}
