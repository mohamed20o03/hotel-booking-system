package com.Abdelwahab.RoomBooking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * A booked add-on line: one {@link Addon} attached to one {@link Reservation}.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code reservation_addon} table. It is
 * the association (join) between a reservation and a catalogue {@link Addon}, carrying the
 * booked {@link #quantity} and the {@link #unitPrice} frozen at booking time so invoices
 * stay accurate even if the catalogue price later changes.
 *
 * <p><strong>Relationships.</strong> {@code @ManyToOne} to {@link Reservation}
 * ({@code CASCADE} on delete — removing a reservation removes its add-on lines) and
 * {@code @ManyToOne} to {@link Addon} ({@code RESTRICT} on delete — a catalogue add-on
 * still referenced by any reservation cannot be removed).
 *
 * <p><strong>Design note.</strong> The line total is intentionally <em>not</em> stored; it
 * is always computed as {@code quantity * unitPrice}, since persisting a derived value
 * would violate 3NF.
 */
@Entity
@Table(name = "reservation_addon")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reservation this line belongs to. {@code CASCADE} on delete: removing the reservation removes its add-on lines. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Reservation reservation;

    /** Catalogue add-on being booked. {@code RESTRICT} on delete: an add-on still referenced here cannot be removed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addon_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Addon addon;

    /** Number of units booked; defaults to {@code 1}. */
    @Column
    @Builder.Default
    private Integer quantity = 1;

    /**
     * Per-unit price frozen at booking time (snapshot of {@link Addon#getPrice()}), so
     * invoices remain accurate even if the catalogue price changes later. The line total is
     * computed as {@code quantity * unitPrice} and is deliberately not stored.
     */
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
