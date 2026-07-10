package com.Abdelwahab.RoomBooking.model;

/**
 * Where a single night's price came from during the day-by-day pricing calculation.
 *
 *   PLAN — a rate plan override existed for that specific date (e.g. a "Summer Promo").
 *   BASE — no override existed, so the room type's guaranteed base rate was used.
 *
 * Stored per night on ReservationNight so an invoice can always explain how each
 * night was priced, even years later.
 */
public enum RateSource {
    PLAN,
    BASE
}
