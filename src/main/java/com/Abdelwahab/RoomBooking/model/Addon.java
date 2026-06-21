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

    // Each hotel manages its own addons and pricing independently
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Hotel hotel;

    @Column(nullable = false, length = 100)
    private String name;

    // e.g., TRANSPORTATION, FOOD, SPA
    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private double price;

    // e.g., PER_PERSON, PER_NIGHT, FLAT_RATE
    @Column(name = "price_unit", nullable = false, length = 20)
    private String priceUnit;

    @Column
    @Builder.Default
    private Boolean available = true;
}
