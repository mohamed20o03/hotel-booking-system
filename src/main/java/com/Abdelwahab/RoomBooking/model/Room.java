package com.Abdelwahab.RoomBooking.model;

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
 * A physical, individually numbered room — the unit assigned to a guest at check-in.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code room} table. A room is a concrete
 * bricks-and-mortar unit (number, floor, building). It is deliberately distinct from
 * {@link RoomType}: the system sells and holds availability by <em>room type</em> count,
 * and only pins a specific {@code Room} onto a {@link Reservation} at check-in (see
 * {@code Reservation.assignedRoom}). A room is also the target of a
 * {@link MaintenanceBlock} that takes it out of service for a date range.
 *
 * <p><strong>Relationships.</strong> Owned by a {@link RoomType} ({@code @ManyToOne},
 * cascade delete).
 */
@Entity
@Table(name = "room")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning room type. Deleting the room type cascades to its rooms. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RoomType roomType;

    @Column(name = "room_number", nullable = false)
    private int roomNumber;

    @Column(name = "floor", nullable = false)
    private int floor;

    @Column(nullable = false, length = 50)
    private String building;
}
