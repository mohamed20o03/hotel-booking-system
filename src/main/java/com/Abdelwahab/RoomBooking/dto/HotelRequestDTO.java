package com.Abdelwahab.RoomBooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating or updating a hotel (admin-only).
 *
 * Field lengths mirror the schema constraints on the hotel table so a bad
 * request is rejected at the edge with a 400, not deep in the database.
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
