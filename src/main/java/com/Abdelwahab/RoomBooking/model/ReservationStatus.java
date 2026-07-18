package com.Abdelwahab.RoomBooking.model;

/**
 * Lifecycle states of a {@link Reservation}.
 *
 * <p>Persisted as a {@code STRING} (see {@code @Enumerated(EnumType.STRING)} on the entity)
 * so the database stores the readable name rather than a fragile ordinal index.
 *
 * <p><strong>Inventory rule.</strong> Only {@link #PENDING} and {@link #CONFIRMED} hold
 * {@link RoomTypeInventory}; the four terminal states ({@link #CHECKED_OUT},
 * {@link #CANCELLED}, {@link #EXPIRED}, {@link #NO_SHOW}) do not. {@link #CHECKED_IN} is an
 * active in-house state that continues to occupy its assigned room.
 *
 * <p><strong>Design note.</strong> Cancellation collapses to a single {@code CANCELLED}
 * state. Refundability is a property of the chosen rate plan
 * ({@code RatePlan.isRefundable}), so it is not encoded into the status. This also fixes an
 * old inventory bug where a cancelled room was never freed because its status
 * ({@code "CANCELLED_REFUNDABLE"}) never equalled the literal {@code "CANCELLED"} used by
 * the availability query.
 */
public enum ReservationStatus {
    /** Created and holding inventory until {@code hold_expires_at}, awaiting payment. Non-terminal. */
    PENDING,
    /** Paid and active booking; still holds inventory. Non-terminal. */
    CONFIRMED,
    /** Guest has arrived and a physical room is assigned. Active, non-terminal. */
    CHECKED_IN,
    /** Stay completed. Terminal; holds no inventory. */
    CHECKED_OUT,
    /** Cancelled by guest or hotel. Terminal; frees inventory. */
    CANCELLED,
    /** Payment hold lapsed before payment was received. Terminal; frees inventory. */
    EXPIRED,
    /** Guest never arrived for a booking that was not otherwise resolved. Terminal. */
    NO_SHOW
}
