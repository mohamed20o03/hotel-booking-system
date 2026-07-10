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
 * A price override for a rate plan on a single calendar date.
 *
 * Rate plans are now SPARSE: they only store rows for dates whose price differs
 * from the room type's base rate. A "Summer Promo" covering Aug 5–15 is simply
 * 11 rows here at the promo price. Any date without a row falls back to
 * RoomType.basePricePerNight during pricing.
 *
 * This is what lets a stay span any range — the pricing engine resolves each
 * night independently instead of requiring the whole stay to fit one plan window.
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
