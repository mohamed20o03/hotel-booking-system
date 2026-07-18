package com.Abdelwahab.RoomBooking.model;

import java.math.BigDecimal;
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

/**
 * A financial transaction recorded against a reservation.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code payment} table. A payment captures
 * a single money movement — deposit, final payment, or refund — for a {@link Reservation},
 * along with the method, gateway provider, and settlement status. A successful payment is
 * what advances a {@code PENDING} reservation to {@code CONFIRMED}. A reservation may
 * accumulate several payment rows over its life.
 *
 * <p><strong>Relationships.</strong> {@code @ManyToOne} to {@link Reservation}
 * ({@code RESTRICT} on delete), so a reservation with payment history cannot be removed and
 * the financial audit trail is preserved.
 *
 * <p><strong>Invariants.</strong> {@link #transactionReference} is <strong>unique</strong>
 * (when present) to make gateway settlements idempotent and prevent double-recording.
 */
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

    /** Reservation this payment settles. {@code RESTRICT} on delete: a reservation with payments cannot be removed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Reservation reservation;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** ISO 4217 currency code of {@link #amount}; defaults to {@code EGP}. */
    @Column(length = 3)
    @Builder.Default
    private String currency = "EGP";

    /** Nature of the movement (e.g. {@code DEPOSIT}, {@code FINAL_PAYMENT}, {@code REFUND}). */
    @Column(nullable = false, length = 20)
    private String type;

    /** Payment instrument used (e.g. {@code CASH}, {@code VISA}, {@code MASTERCARD}). */
    @Column(nullable = false, length = 20)
    private String method;

    /** Settlement channel (e.g. {@code STRIPE}, {@code PAYMOB}, {@code FRONT_DESK}). */
    @Column(nullable = false, length = 50)
    private String provider;

    /** External transaction ID returned by the payment gateway; <strong>unique</strong> for idempotent settlement. */
    @Column(name = "transaction_reference", length = 100, unique = true)
    private String transactionReference;

    /** Settlement state (e.g. {@code PENDING}, {@code SUCCESS}, {@code FAILED}, {@code REFUNDED}). */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
