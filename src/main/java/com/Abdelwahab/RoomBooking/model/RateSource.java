package com.Abdelwahab.RoomBooking.model;

/**
 * Where a single night's price came from during the day-by-day pricing calculation.
 *
 * <p>Stored per night on {@link ReservationNight} so an invoice can always explain how each
 * night was priced, even years later.
 */
public enum RateSource {
    /** A {@link RatePlanRate} override existed for that specific date (e.g. a "Summer Promo"). */
    PLAN,
    /** No override existed, so the room type's guaranteed {@code basePricePerNight} was used. */
    BASE
}
