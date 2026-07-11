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
 * A rate plan bundles the guest-facing policies (refundable? breakfast? minimum
 * stay?) for a room type. Its actual per-night prices live in RatePlanRate as
 * sparse date overrides; any date without an override is billed at the room
 * type's base rate. Currency comes from the room type, so a stay is always
 * priced in a single currency. This separation is what lets a stay span dates
 * that no single plan fully covers.
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RoomType roomType;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "min_stay_nights")
    @Builder.Default
    private Integer minStayNights = 1;

    @Column(name = "breakfast_included")
    @Builder.Default
    private Boolean breakfastIncluded = false;

    @Column(name = "is_refundable")
    @Builder.Default
    private Boolean isRefundable = true;
}
