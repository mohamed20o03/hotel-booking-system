package com.Abdelwahab.RoomBooking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Reservation;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // Guest looks up their booking via the confirmation email/number
    Optional<Reservation> findByConfirmationNumber(String confirmationNumber);

    // Show a guest all their past and upcoming bookings (newest first)
    List<Reservation> findByGuestIdOrderByCheckInDateDesc(Long guestId);
}