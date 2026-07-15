package com.Abdelwahab.RoomBooking.model;

/**
 * Lifecycle states of a reservation.
 *
 * Persisted as a STRING (see @Enumerated(EnumType.STRING) on the entity) so the
 * database stores the readable name, not a fragile ordinal index.
 *
 * NOTE: cancellation collapses to a single CANCELLED state. Refundability is a
 * property of the chosen rate plan (ratePlan.isRefundable), so it does not need
 * to be encoded into the status. This also fixes the old inventory bug where a
 * cancelled room was never freed because its status ("CANCELLED_REFUNDABLE")
 * never equalled the literal "CANCELLED" used by the availability query.
 */
public enum ReservationStatus {
    PENDING,      // created, holding inventory until hold_expires_at, awaiting payment
    CONFIRMED,    // paid and active booking, holds inventory
    CHECKED_IN,   // guest has arrived, physical room assigned
    CHECKED_OUT,  // stay completed
    CANCELLED,    // cancelled by guest or hotel — frees inventory
    EXPIRED,      // payment hold lapsed before payment — frees inventory
    NO_SHOW       // guest never arrived
}
