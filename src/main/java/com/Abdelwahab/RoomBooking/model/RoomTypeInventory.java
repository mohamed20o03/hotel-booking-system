package com.Abdelwahab.RoomBooking.model;

import java.time.LocalDate;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sellable inventory for a room type on a single date — the allotment calendar.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code room_type_inventory} table, one
 * row per ({@link RoomType}, night). The hotel "opens" a date for sale by having a row
 * here: {@link #totalRooms} is the capacity for that night and {@link #bookedCount} is how
 * many are currently held by active reservations. A room type is available for a stay only
 * if <strong>every</strong> night in {@code [checkIn, checkOut)} has a row with
 * {@code bookedCount < totalRooms} (see {@link #remaining()}).
 *
 * <p><strong>Concurrency role.</strong> This is the row locked <strong>pessimistically</strong>
 * during booking. Selling by room-type count (rather than pinning a physical room at
 * booking) together with that lock is what prevents two guests from grabbing the last room
 * for overlapping dates.
 *
 * <p><strong>Invariants.</strong> A <strong>unique</strong> constraint on
 * ({@code room_type_id}, {@code date}) guarantees at most one inventory row per room type
 * per night.
 */
@Entity
@Table(name = "room_type_inventory",
       uniqueConstraints = @UniqueConstraint(columnNames = {"room_type_id", "date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomTypeInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Room type this allotment belongs to. Deleting the room type cascades to its inventory rows. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RoomType roomType;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Capacity for this night. Seeded from {@link RoomType#getTotalRooms()} but stored
     * per date so it can later be overridden (closeouts, maintenance, over-sell).
     */
    @Column(name = "total_rooms", nullable = false)
    private int totalRooms;

    /** How many rooms of this type are currently held for this night by active reservations. */
    @Column(name = "booked_count", nullable = false)
    private int bookedCount;

    /** Rooms still sellable for this night. */
    public int remaining() {
        return totalRooms - bookedCount;
    }
}
