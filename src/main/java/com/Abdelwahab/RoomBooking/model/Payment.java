package com.Abdelwahab.RoomBooking.model;

import java.time.LocalDateTime;

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
@Table(name = "payment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Reservation reservation;

    @Column(nullable = false, precision = 19, scale = 2)
    private double amount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "EGP";

    // e.g., DEPOSIT, FINAL_PAYMENT, REFUND
    @Column(nullable = false, length = 20)
    private String type;

    // e.g., CASH, VISA, MASTERCARD
    @Column(nullable = false, length = 20)
    private String method;

    // e.g., STRIPE, PAYMOB, FRONT_DESK
    @Column(nullable = false, length = 50)
    private String provider;

    // External transaction ID returned by the payment gateway
    @Column(name = "transaction_reference", length = 100, unique = true)
    private String transactionReference;

    // e.g., PENDING, SUCCESS, FAILED, REFUNDED
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
