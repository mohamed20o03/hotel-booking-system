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
 * A purchasable extra offered by a hotel — the catalogue definition, not a booked line.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code addon} table. An add-on is an
 * ancillary product or service (transport, food, spa, ...) that a hotel sells alongside a
 * room. It is the catalogue template; the act of attaching one to a booking, with its
 * frozen price and quantity, is a separate {@link ReservationAddon} line.
 *
 * <p><strong>Relationships.</strong> Owned by a {@link Hotel} ({@code @ManyToOne}, cascade
 * delete), since each hotel manages its own add-ons and pricing independently. Referenced
 * by {@link ReservationAddon} when booked.
 */
@Entity
@Table(name = "addon")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Addon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning hotel. Deleting the hotel cascades to its add-ons. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Hotel hotel;

    @Column(nullable = false, length = 100)
    private String name;

    /** Product grouping (e.g. {@code TRANSPORTATION}, {@code FOOD}, {@code SPA}). */
    @Column(nullable = false, length = 50)
    private String category;

    /** Current catalogue price; snapshotted onto {@link ReservationAddon#getUnitPrice()} when booked. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    /** Billing basis for {@link #price} (e.g. {@code PER_PERSON}, {@code PER_NIGHT}, {@code FLAT_RATE}). */
    @Column(name = "price_unit", nullable = false, length = 20)
    private String priceUnit;

    /** Whether the add-on is currently offered for sale; defaults to {@code true}. */
    @Column
    @Builder.Default
    private Boolean available = true;
}
