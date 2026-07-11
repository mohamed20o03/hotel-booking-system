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
 * The hotel "opens" a date for sale by having a row here; total_rooms is the
 * capacity for that night and booked_count is how many are currently held by
 * active reservations. A room type is available for a stay only if EVERY night
 * in [checkIn, checkOut) has a row with booked_count < total_rooms.
 *
 * This is what lets the system sell by room-type count (not by pinning a
 * physical room at booking) and, together with pessimistic locking during
 * booking, prevents two guests from grabbing the last room for overlapping dates.
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RoomType roomType;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    // Capacity for this night. Seeded from RoomType.totalRooms but stored per
    // date so it can later be overridden (closeouts, maintenance, over-sell).
    @Column(name = "total_rooms", nullable = false)
    private int totalRooms;

    // How many rooms of this type are currently held for this night.
    @Column(name = "booked_count", nullable = false)
    private int bookedCount;

    /** Rooms still sellable for this night. */
    public int remaining() {
        return totalRooms - bookedCount;
    }
}
