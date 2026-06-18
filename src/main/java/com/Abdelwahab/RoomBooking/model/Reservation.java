package com.Abdelwahab.RoomBooking.model;

import java.time.LocalDate;
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

    // The guest who made this reservation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Guest guest;

    // The rate plan chosen at booking time (determines room type and price)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_plan_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private RatePlan ratePlan;

    // Nullable: a physical room may not be assigned at booking time
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_room_id", referencedColumnName = "id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Room assignedRoom;

    @Column(name = "confirmation_number", nullable = false, length = 50, unique = true)
    private String confirmationNumber;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "num_guests", nullable = false)
    private int numGuests;

    // Frozen at booking time to protect against future price changes
    @Column(name = "total_price", nullable = false, precision = 19, scale = 2)
    private double totalPrice;

    // e.g., CONFIRMED, CANCELLED, CHECKED_IN
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
