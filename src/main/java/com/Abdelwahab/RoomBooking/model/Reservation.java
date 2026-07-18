package com.Abdelwahab.RoomBooking.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A guest's booking of a room type for a stay — the aggregate root of the booking domain.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code reservation} table. A reservation
 * ties a {@link Guest} to a {@link RatePlan} (and hence a room type) for the half-open date
 * range {@code [checkInDate, checkOutDate)}, freezes its price as a day-by-day
 * {@link ReservationNight} breakdown, and tracks its position in the lifecycle below.
 *
 * <p><strong>Status lifecycle.</strong> The happy path runs
 * {@code PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT}. Off-path terminal states are
 * {@code EXPIRED} (payment hold lapsed), {@code CANCELLED} (guest or hotel), and
 * {@code NO_SHOW}. See {@link ReservationStatus} for each constant's meaning.
 *
 * <p><strong>Inventory invariant.</strong> Only {@code PENDING} and {@code CONFIRMED}
 * reservations hold {@link RoomTypeInventory}; every other state has released its rooms.
 *
 * <p><strong>Pay-vs-expire race.</strong> A {@code PENDING} hold can be resolved
 * concurrently by an incoming payment (→ {@code CONFIRMED}) and by the expiry sweep
 * (→ {@code EXPIRED}). The {@link #version} optimistic-lock column guards this race so only
 * one of them wins; the loser fails with an {@code OptimisticLockException}.
 *
 * <p><strong>Relationships.</strong> {@code @ManyToOne} to {@link Guest} and
 * {@link RatePlan} (both {@code RESTRICT} on delete, so a referenced guest/plan cannot be
 * removed); an optional {@code @ManyToOne} to the assigned physical {@link Room}
 * ({@code SET_NULL} on delete). It owns its {@link ReservationNight}s
 * ({@code @OneToMany}, {@code CascadeType.ALL} + {@code orphanRemoval}).
 */
@Entity
@Table(name = "reservation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Guest who owns this reservation. {@code RESTRICT} on delete: a guest with reservations cannot be removed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Guest guest;

    /**
     * Rate plan chosen at booking time, which determines both the room type sold and the
     * per-night pricing basis. {@code RESTRICT} on delete: a plan in use cannot be removed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_plan_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private RatePlan ratePlan;

    /**
     * Physical room assigned to the stay. Null until check-in, since the system sells by
     * room-type count rather than pinning a room at booking. {@code SET_NULL} on delete so
     * retiring a room does not destroy its booking history.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_room_id", referencedColumnName = "id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Room assignedRoom;

    /** Human-facing booking reference; <strong>unique</strong> across all reservations. */
    @Column(name = "confirmation_number", nullable = false, length = 50, unique = true)
    private String confirmationNumber;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "num_guests", nullable = false)
    private int numGuests;

    /**
     * Grand total frozen at booking time to protect against future price changes. Equals
     * the sum of every {@link ReservationNight#getRateAmount()} in {@link #nights}.
     */
    @Column(name = "total_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;

    /** Lifecycle state; persisted as its readable name (not an ordinal). See {@link ReservationStatus}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    /**
     * Instant until which a {@code PENDING} hold on inventory is guaranteed. The expiry
     * sweep releases holds past this time. Null once the reservation leaves {@code PENDING}
     * (paid, cancelled, or expired).
     */
    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    /**
     * Optimistic-lock guard (JPA {@code @Version}) for the pay-vs-expire race: a concurrent
     * payment and the expiry sweep cannot both act on the same {@code PENDING} hold — the
     * loser gets an {@code OptimisticLockException}.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Frozen per-night price breakdown (day-by-day billing). {@code CascadeType.ALL} plus
     * {@code orphanRemoval}: nights are owned entirely by their reservation and have no
     * independent lifecycle.
     */
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReservationNight> nights = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
