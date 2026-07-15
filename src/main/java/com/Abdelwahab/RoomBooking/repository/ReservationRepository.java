package com.Abdelwahab.RoomBooking.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.ReservationStatus;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // Guest looks up their booking via the confirmation email/number
    Optional<Reservation> findByConfirmationNumber(String confirmationNumber);

    // Show a guest all their past and upcoming bookings (newest first)
    List<Reservation> findByGuestIdOrderByCheckInDateDesc(Long guestId);

    // Holds whose payment window has lapsed — fed to the expiry sweep. Scoped to
    // PENDING so paid/cancelled reservations are never touched.
    List<Reservation> findByStatusAndHoldExpiresAtBefore(ReservationStatus status, LocalDateTime cutoff);
}