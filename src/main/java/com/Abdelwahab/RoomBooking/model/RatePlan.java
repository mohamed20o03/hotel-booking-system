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
 * A bookable product/policy container for a room type — NOT a price by itself.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code rate_plan} table. A rate plan
 * bundles the guest-facing policies (refundable? breakfast? minimum stay?) for a
 * {@link RoomType}. Its actual per-night prices live in {@link RatePlanRate} as sparse date
 * overrides; any date without an override is billed at the room type's base rate. Currency
 * comes from the room type, so a stay is always priced in a single currency. This
 * separation of policy from price is what lets a stay span dates that no single plan fully
 * covers.
 *
 * <p><strong>Relationships.</strong> Owned by a {@link RoomType} ({@code @ManyToOne},
 * cascade delete). It is the parent of its {@link RatePlanRate} overrides and is referenced
 * by {@link Reservation#getRatePlan()} to record the plan chosen at booking time.
 */
@Entity
@Table(name = "rate_plan")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Room type this plan sells. Deleting the room type cascades to its rate plans. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RoomType roomType;

    @Column(nullable = false, length = 50)
    private String name;

    /** Minimum number of nights a stay under this plan must span; defaults to {@code 1}. */
    @Column(name = "min_stay_nights")
    @Builder.Default
    private Integer minStayNights = 1;

    @Column(name = "breakfast_included")
    @Builder.Default
    private Boolean breakfastIncluded = false;

    /** Whether a cancellation under this plan is refundable; the policy is carried here rather than on the reservation status. Defaults to {@code true}. */
    @Column(name = "is_refundable")
    @Builder.Default
    private Boolean isRefundable = true;
}
