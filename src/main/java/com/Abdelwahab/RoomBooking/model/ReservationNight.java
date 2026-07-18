package com.Abdelwahab.RoomBooking.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One night of a {@link Reservation}, with its price frozen at booking time.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code reservation_night} table, one row
 * per date in the stay. Storing the breakdown (not just the grand total) is what real
 * PMS/folio systems do: it supports partial cancellations, stay extensions, per-night
 * taxes, refunds, and revenue reporting (ADR/RevPAR). The sum of {@link #rateAmount} across
 * a reservation's nights equals {@link Reservation#getTotalPrice()}.
 *
 * <p><strong>Relationships.</strong> Owned by its {@link Reservation} ({@code @ManyToOne},
 * cascade delete); the reservation drives its full lifecycle via cascade and orphan removal.
 *
 * <p><strong>Invariants.</strong> A <strong>unique</strong> constraint on
 * ({@code reservation_id}, {@code date}) guarantees a single priced row per night.
 */
@Entity
@Table(name = "reservation_night", uniqueConstraints = @UniqueConstraint(columnNames = {"reservation_id", "date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationNight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Reservation reservation;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "rate_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal rateAmount;

    /** Whether this night was priced from a rate-plan override or the base rate. See {@link RateSource}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private RateSource source;
}
