package com.Abdelwahab.RoomBooking.model;

import java.math.BigDecimal;
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
 * A price override for a {@link RatePlan} on a single calendar date.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code rate_plan_rate} table. Rate plans
 * are <strong>sparse</strong>: they store rows only for dates whose price differs from the
 * room type's base rate. A "Summer Promo" covering Aug 5–15 is simply 11 rows here at the
 * promo price; any date without a row falls back to {@code RoomType.basePricePerNight}
 * during pricing (recorded on the resulting night as {@link RateSource#BASE}).
 *
 * <p>This is what lets a stay span any range — the pricing engine resolves each night
 * independently instead of requiring the whole stay to fit one plan window.
 *
 * <p><strong>Relationships.</strong> Owned by a {@link RatePlan} ({@code @ManyToOne},
 * cascade delete).
 *
 * <p><strong>Invariants.</strong> A <strong>unique</strong> constraint on
 * ({@code rate_plan_id}, {@code date}) guarantees at most one override per plan per date.
 */
@Entity
@Table(name = "rate_plan_rate", uniqueConstraints = @UniqueConstraint(columnNames = {"rate_plan_id", "date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatePlanRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_plan_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RatePlan ratePlan;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
}
