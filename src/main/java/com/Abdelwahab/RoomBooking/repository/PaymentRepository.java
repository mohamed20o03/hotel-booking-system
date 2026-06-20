package com.Abdelwahab.RoomBooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByReservationId(Long reservationId);

    // Sum all successful payments for a reservation (used to calculate balance due at checkout)
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.reservation.id = :reservationId AND p.status = 'SUCCESS'")
    double sumSuccessfulPaymentsByReservationId(@Param("reservationId") Long reservationId);
}
