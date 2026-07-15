package com.Abdelwahab.RoomBooking.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByReservationId(Long reservationId);

    // Sum all successful payments for a reservation (used to calculate balance due at checkout).
    // Returns BigDecimal to stay in exact decimal arithmetic — money must never round-trip through double.
    // COALESCE(..., 0) turns the no-rows NULL into a zero total.
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.reservation.id = :reservationId AND p.status = 'SUCCESS'")
    BigDecimal sumSuccessfulPaymentsByReservationId(@Param("reservationId") Long reservationId);
}
