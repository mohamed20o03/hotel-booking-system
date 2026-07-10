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

@Entity
@Table(name = "room_type")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomType {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Hotel hotel;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_occupancy", nullable = false)
    private int maxOccupancy;

    @Column(name = "total_rooms", nullable = false)
    private int totalRooms;

    // Guaranteed year-round fallback price. Every night that has no rate plan
    // override is billed at this rate, so a booking can never lack a price.
    @Column(name = "base_price_per_night", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePricePerNight;

    // Currency of the base rate. All rate plans on this room type must match it,
    // otherwise a day-by-day total would silently sum across currencies.
    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "EGP";
}
