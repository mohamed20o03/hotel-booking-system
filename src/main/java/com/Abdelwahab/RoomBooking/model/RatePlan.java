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

    @Column(name = "price_per_night", nullable = false)
    private double pricePerNight;

    @Column(length = 3)
    @Builder.Default
    private String currency = "EGP";

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

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
