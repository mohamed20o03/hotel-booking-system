package com.Abdelwahab.RoomBooking.model;

import java.math.BigDecimal;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A category of interchangeable rooms and the unit of sellable inventory.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code room_type} table. A room type
 * (e.g. "Deluxe Double") is what the system actually sells: availability, pricing, and
 * booking are all expressed against a room type rather than against a specific physical
 * room. This is deliberately distinct from {@link Room} — a room type is the sellable,
 * count-based abstraction, whereas a {@code Room} is a concrete numbered unit assigned to
 * a guest only at check-in.
 *
 * <p><strong>Relationships.</strong> Owned by a {@link Hotel} ({@code @ManyToOne}, cascade
 * delete). It is in turn the parent of its physical {@link Room}s, its {@link RatePlan}s,
 * and its per-night {@link RoomTypeInventory} rows; those associations are modelled as
 * back-references on the child entities.
 *
 * <p><strong>Pricing invariants.</strong> {@link #basePricePerNight} is the guaranteed
 * fallback rate, so a night can never lack a price. All rate plans on this room type must
 * quote in the room type's {@link #currency}, otherwise a day-by-day total could silently
 * sum across currencies.
 */
@Entity
@Table(name = "room_type")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning hotel. Deleting the hotel cascades to its room types. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Hotel hotel;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_occupancy", nullable = false)
    private int maxOccupancy;

    /** Number of physical rooms of this type; seeds the nightly {@link RoomTypeInventory} capacity. */
    @Column(name = "total_rooms", nullable = false)
    private int totalRooms;

    /**
     * Guaranteed year-round fallback price. Every night that has no {@link RatePlanRate}
     * override is billed at this rate, so a booking can never lack a price.
     */
    @Column(name = "base_price_per_night", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePricePerNight;

    /**
     * Currency of the base rate. All rate plans on this room type must match it, otherwise
     * a day-by-day total would silently sum across currencies. Defaults to {@code EGP}.
     */
    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "EGP";
}
