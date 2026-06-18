package com.Abdelwahab.RoomBooking.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "guest")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    // Store as a BCrypt hash, never plain text
    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String phone;

    // ISO 3166-1 alpha-2 country code (e.g., EG, US)
    @Column(nullable = false, length = 2)
    private String nationality;

    // e.g., PASSPORT, NATIONAL_ID
    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 100)
    private String documentNumber;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    // e.g., STANDARD, SILVER, GOLD
    @Column(name = "loyalty_tier", length = 20)
    @Builder.Default
    private String loyaltyTier = "STANDARD";

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
