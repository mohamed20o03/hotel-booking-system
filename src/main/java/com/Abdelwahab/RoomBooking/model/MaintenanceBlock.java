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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A period during which a physical room is out of service and cannot be occupied.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code maintenance_block} table. A block
 * takes a specific {@link Room} off the assignable pool for the date range
 * {@code [startDate, endDate]} (renovation, deep clean, damage, ...). Because blocks target
 * a physical room rather than a room type, they constrain room assignment rather than the
 * count-based {@link RoomTypeInventory} used for selling.
 *
 * <p><strong>Relationships.</strong> {@code @ManyToOne} to {@link Room} ({@code CASCADE} on
 * delete — removing the room removes its blocks).
 */
@Entity
@Table(name = "maintenance_block")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Physical room taken out of service. Deleting the room cascades to its maintenance blocks. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Room room;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 255)
    private String reason;
}
