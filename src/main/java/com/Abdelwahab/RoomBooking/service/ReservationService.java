package com.Abdelwahab.RoomBooking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.AvailabilityResponseDTO;
import com.Abdelwahab.RoomBooking.dto.RatePlanResponseDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationConfirmationDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationRequestDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationResponseDTO;
import com.Abdelwahab.RoomBooking.exception.NoAvailabilityException;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.model.RatePlan;
import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.Room;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;
import com.Abdelwahab.RoomBooking.repository.RatePlanRepository;
import com.Abdelwahab.RoomBooking.repository.ReservationRepository;
import com.Abdelwahab.RoomBooking.repository.RoomRepository;
import com.Abdelwahab.RoomBooking.repository.RoomTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RatePlanRepository ratePlanRepository;
    private final GuestRepository guestRepository;
    private final ReservationRepository reservationRepository;

    // ─────────────────────────────────────────────────────────────
    // STEP 1: SEARCH — Guest looks for available options
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns only the room types that satisfy BOTH conditions:
     *   1. At least one physical room is free for the requested dates.
     *   2. At least one active Rate Plan (price) exists for those dates.
     *
     * This ensures we never show a room that has no price,
     * and never show a price for a fully booked room.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityResponseDTO> searchAvailableOptions(
            Long hotelId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int numGuests) {

        List<RoomType> roomTypes = roomTypeRepository.findByHotelId(hotelId);
        List<AvailabilityResponseDTO> results = new ArrayList<>();

        for (RoomType type : roomTypes) {

            // Skip room types that can't accommodate the requested party size
            if (type.getMaxOccupancy() < numGuests) continue;

            // 1. Check physical inventory
            List<Room> availableRooms = roomRepository.findAvailableRooms(
                    type.getId(), checkInDate, checkOutDate);

            if (availableRooms.isEmpty()) continue; // Nothing physically available

            // 2. Check price availability (Rate Plans)
            List<RatePlan> ratePlans = ratePlanRepository.findOverlappingRatePlans(
                    type.getId(), checkInDate, checkOutDate);

            if (ratePlans.isEmpty()) continue; // No active pricing found

            // 3. Build the DTO — this room type is valid to show to the guest
            List<RatePlanResponseDTO> ratePlanDTOs = ratePlans.stream()
                    .map(rp -> new RatePlanResponseDTO(
                            rp.getId(),
                            rp.getName(),
                            rp.getPricePerNight(),
                            rp.getCurrency(),
                            rp.getValidFrom(),
                            rp.getValidTo(),
                            rp.getMinStayNights(),
                            rp.getBreakfastIncluded(),
                            rp.getIsRefundable()))
                    .toList();

            results.add(new AvailabilityResponseDTO(
                    type.getId(),
                    type.getName(),
                    type.getDescription(),
                    type.getMaxOccupancy(),
                    availableRooms.size(),
                    type.getBasePricePerNight(),
                    ratePlanDTOs));
        }

        return results;
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 2: BOOK — Guest confirms and creates a reservation
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a confirmed reservation. The flow is:
     *   1. Validate guest and rate plan exist.
     *   2. Validate the stay meets the rate plan's minimum night requirement.
     *   3. Double-check inventory (race condition protection).
     *   4. Auto-assign the first available physical room.
     *   5. Calculate the total price.
     *   6. Save the reservation and return a confirmation.
     */
    @Transactional
    public ReservationConfirmationDTO createBooking(ReservationRequestDTO request) {

        // 1. Fetch Guest
        Guest guest = guestRepository.findById(request.guestId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Guest not found with ID: " + request.guestId()));

        // 2. Fetch Rate Plan
        RatePlan ratePlan = ratePlanRepository.findById(request.ratePlanId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rate plan not found with ID: " + request.ratePlanId()));

        LocalDate checkIn = request.checkInDate();
        LocalDate checkOut = request.checkOutDate();
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);

        // 3. Validate minimum stay requirement of the chosen rate plan
        if (nights < ratePlan.getMinStayNights()) {
            throw new IllegalArgumentException(String.format(
                    "The '%s' rate plan requires a minimum stay of %d nights, but you requested %d.",
                    ratePlan.getName(), ratePlan.getMinStayNights(), nights));
        }

        // 4. Validate rate plan is still active for requested dates
        if (checkIn.isBefore(ratePlan.getValidFrom()) || checkOut.isAfter(ratePlan.getValidTo())) {
            throw new NoAvailabilityException(
                    "The selected rate plan is not valid for the requested dates.");
        }

        // 5. Double-check physical room availability (protects against race conditions)
        List<Room> availableRooms = roomRepository.findAvailableRooms(
                ratePlan.getRoomType().getId(), checkIn, checkOut);

        if (availableRooms.isEmpty()) {
            throw new NoAvailabilityException(
                    "Sorry, no rooms are available for the selected room type and dates. Please try different dates.");
        }

        // 6. Auto-assign the first available room
        Room assignedRoom = availableRooms.get(0);

        // 7. Calculate total price (price per night * number of nights)
        double totalPrice = ratePlan.getPricePerNight() * (double) nights;

        // 8. Build and save Reservation
        Reservation reservation = Reservation.builder()
                .guest(guest)
                .ratePlan(ratePlan)
                .assignedRoom(assignedRoom)
                .confirmationNumber(generateConfirmationNumber())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .numGuests(request.numGuests())
                .totalPrice(totalPrice)
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .build();

        reservation = reservationRepository.save(reservation);

        // 9. Return a rich confirmation response
        return new ReservationConfirmationDTO(
                reservation.getId(),
                reservation.getConfirmationNumber(),
                guest.getFirstName() + " " + guest.getLastName(),
                ratePlan.getRoomType().getName(),
                assignedRoom.getId(),
                checkIn,
                checkOut,
                request.numGuests(),
                totalPrice,
                ratePlan.getCurrency(),
                reservation.getStatus(),
                reservation.getCreatedAt());
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 3: LOOKUP — Guest retrieves their bookings
    // ─────────────────────────────────────────────────────────────

    /**
     * Looks up a single reservation by confirmation number.
     * Used in "Manage My Booking" or check-in flows.
     */
    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationByConfirmationNumber(String confirmationNumber) {
        Reservation reservation = reservationRepository.findByConfirmationNumber(confirmationNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No reservation found with confirmation number: " + confirmationNumber));
        return toDTO(reservation);
    }

    /**
     * Returns all reservations for a given guest (newest first).
     */
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getReservationsByGuest(Long guestId) {
        if (!guestRepository.existsById(guestId)) {
            throw new ResourceNotFoundException("Guest not found with ID: " + guestId);
        }
        return reservationRepository.findByGuestIdOrderByCheckInDateDesc(guestId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 4: CANCEL — Guest cancels a booking
    // ─────────────────────────────────────────────────────────────

    /**
     * Cancels a reservation. Only CONFIRMED reservations can be cancelled.
     * If the rate plan is non-refundable, the status becomes CANCELLED_NON_REFUNDABLE.
     */
    @Transactional
    public void cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found with ID: " + reservationId));

        // Make sure the guest owns this reservation using SecurityContext
        String currentUsername = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getName();

        if (!reservation.getGuest().getEmail().equals(currentUsername)) {
            throw new IllegalArgumentException("You do not have permission to cancel this reservation.");
        }

        if (!"CONFIRMED".equals(reservation.getStatus())) {
            throw new IllegalArgumentException(
                    "Only CONFIRMED reservations can be cancelled. Current status: " + reservation.getStatus());
        }

        // Determine correct cancellation status based on rate plan refundability
        String cancelledStatus = Boolean.TRUE.equals(reservation.getRatePlan().getIsRefundable())
                ? "CANCELLED_REFUNDABLE"
                : "CANCELLED_NON_REFUNDABLE";

        reservation.setStatus(cancelledStatus);
        reservationRepository.save(reservation);
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    /**
     * Generates a short, readable confirmation number like "AB12CD34".
     */
    private String generateConfirmationNumber() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }

    /**
     * Maps a Reservation entity to a ReservationResponseDTO.
     * Safely extracts nested data from lazy-loaded relationships.
     */
    private ReservationResponseDTO toDTO(Reservation reservation) {
        Guest guest = reservation.getGuest();
        RatePlan ratePlan = reservation.getRatePlan();
        Room assignedRoom = reservation.getAssignedRoom();

        return new ReservationResponseDTO(
                reservation.getId(),
                reservation.getConfirmationNumber(),
                reservation.getStatus(),
                guest.getId(),
                guest.getFirstName() + " " + guest.getLastName(),
                ratePlan.getRoomType().getName(),
                assignedRoom != null ? assignedRoom.getId() : null,
                ratePlan.getName(),
                ratePlan.getPricePerNight(),
                ratePlan.getCurrency(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                reservation.getNumGuests(),
                reservation.getTotalPrice(),
                reservation.getCreatedAt()
        );
    }
}
