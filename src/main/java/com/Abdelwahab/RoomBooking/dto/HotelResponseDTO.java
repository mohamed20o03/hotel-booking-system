package com.Abdelwahab.RoomBooking.dto;

/**
 * Response DTO exposing a hotel's public details, serialized to the JSON body of
 * the read endpoints under {@code /api/hotels} ({@code GET} list and
 * {@code GET /{id}}). It is the outbound wire shape for a hotel; the
 * {@code Hotel} JPA entity is never serialized directly, so lazy associations and
 * audit fields stay off the response. Note this projection is narrower than the
 * request contract (address is omitted here).
 *
 * @param id the hotel's stable identifier.
 * @param name the hotel's display name.
 * @param city the city the hotel is in.
 * @param country the country.
 * @param starRating the 0–5 star quality rating.
 * @param phone the front-desk contact number.
 * @param email the hotel contact address.
 * @param timezone the IANA zone id used for local check-in/out semantics, if set.
 */
public record HotelResponseDTO(
    Long id,
    String name,
    String city,
    String country,
    Integer starRating,
    String phone,
    String email,
    String timezone
) {}
