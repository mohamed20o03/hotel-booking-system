package com.Abdelwahab.RoomBooking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.AvailabilityResponseDTO;
import com.Abdelwahab.RoomBooking.dto.NightlyPriceDTO;
import com.Abdelwahab.RoomBooking.dto.RatePlanResponseDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationConfirmationDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationRequestDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationResponseDTO;
import com.Abdelwahab.RoomBooking.exception.NoAvailabilityException;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.model.RatePlan;
import com.Abdelwahab.RoomBooking.model.RateSource;
import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.ReservationNight;
import com.Abdelwahab.RoomBooking.model.ReservationStatus;
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
    private final PricingService pricingService;

    // ─────────────────────────────────────────────────────────────
    // STEP 1: SEARCH — Guest looks for available options
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns every room type that has at least one physical room free for the
     * requested dates, each paired with its rate plans priced day-by-day for
     * those exact dates.
     *
     * The base rate guarantees every night has a price, so a room type is shown
     * whenever it is physically available — a stay no longer has to fit inside a
     * single rate plan's date window.
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

            // Check physical inventory for the whole stay
            List<Room> availableRooms = roomRepository.findAvailableRooms(
                    type.getId(), checkInDate, checkOutDate);

            if (availableRooms.isEmpty()) continue; // Nothing physically available

            // Price every rate plan of this type for the requested dates
            List<RatePlanResponseDTO> ratePlanDTOs = ratePlanRepository.findByRoomTypeId(type.getId())
                    .stream()
                    .map(rp -> {
                        var quote = pricingService.quote(type, rp, checkInDate, checkOutDate);
                        return new RatePlanResponseDTO(
                                rp.getId(),
                                rp.getName(),
                                rp.getCurrency(),
                                rp.getMinStayNights(),
                                rp.getBreakfastIncluded(),
                                rp.getIsRefundable(),
                                quote.nightCount(),
                                quote.total());
                    })
                    .toList();

            results.add(new AvailabilityResponseDTO(
                    type.getId(),
                    type.getName(),
                    type.getDescription(),
                    type.getMaxOccupancy(),
                    availableRooms.size(),
                    type.getBasePricePerNight(),
                    type.getCurrency(),
                    ratePlanDTOs));
        }

        return results;
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 2: BOOK — Guest confirms and creates a reservation
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a confirmed reservation under a single chosen rate plan. The flow is:
     *   1. Resolve the authenticated guest and the chosen rate plan (+ its room type).
     *   2. Enforce the plan's minimum-stay policy.
     *   3. Confirm the room type is physically available for the stay.
     *   4. Price the stay day-by-day (plan overrides + base-rate fallback).
     *   5. Persist the reservation with its frozen per-night breakdown.
     *
     * NOTE (Phase 2): a physical room is still assigned here to keep the current
     * availability query working. Phase 2 replaces this with count-based room-type
     * inventory + a pessimistic lock, and assigns the physical room at check-in.
     */
    @Transactional
    public ReservationConfirmationDTO createBooking(ReservationRequestDTO request) {

        // 1. Guest from the JWT — no guestId in the request body
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Guest guest = guestRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated guest not found: " + currentEmail));

        // 2. Chosen rate plan drives both policy and pricing; room type derives from it
        RatePlan ratePlan = ratePlanRepository.findById(request.ratePlanId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rate plan not found with ID: " + request.ratePlanId()));
        RoomType roomType = ratePlan.getRoomType();

        LocalDate checkIn = request.checkInDate();
        LocalDate checkOut = request.checkOutDate();
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);

        // 3. Party size must fit the room type
        if (roomType.getMaxOccupancy() < request.numGuests()) {
            throw new IllegalArgumentException(String.format(
                    "Room type '%s' holds at most %d guests, but %d were requested.",
                    roomType.getName(), roomType.getMaxOccupancy(), request.numGuests()));
        }

        // 4. Minimum-stay policy of the chosen plan
        if (nights < ratePlan.getMinStayNights()) {
            throw new IllegalArgumentException(String.format(
                    "The '%s' rate plan requires a minimum stay of %d nights, but you requested %d.",
                    ratePlan.getName(), ratePlan.getMinStayNights(), nights));
        }

        // 5. Physical availability for the whole stay (race-safe inventory is Phase 2)
        List<Room> availableRooms = roomRepository.findAvailableRooms(
                roomType.getId(), checkIn, checkOut);

        if (availableRooms.isEmpty()) {
            throw new NoAvailabilityException(
                    "Sorry, no rooms are available for the selected room type and dates. Please try different dates.");
        }

        // 6. Price the stay day-by-day
        PricingService.PriceQuote quote = pricingService.quote(roomType, ratePlan, checkIn, checkOut);

        // 7. Build the reservation shell (Phase 2 will drop booking-time assignment)
        Room assignedRoom = availableRooms.get(0);
        Reservation reservation = Reservation.builder()
                .guest(guest)
                .ratePlan(ratePlan)
                .assignedRoom(assignedRoom)
                .confirmationNumber(generateConfirmationNumber())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .numGuests(request.numGuests())
                .totalPrice(quote.total())
                .status(ReservationStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();

        // 8. Attach the frozen per-night breakdown
        for (PricingService.NightlyPrice np : quote.nights()) {
            reservation.getNights().add(ReservationNight.builder()
                    .reservation(reservation)
                    .date(np.date())
                    .rateAmount(np.amount())
                    .source(np.source())
                    .build());
        }

        reservation = reservationRepository.save(reservation); // cascades nights

        // 9. Rich confirmation with the breakdown
        return new ReservationConfirmationDTO(
                reservation.getId(),
                reservation.getConfirmationNumber(),
                guest.getFirstName() + " " + guest.getLastName(),
                roomType.getName(),
                assignedRoom.getId(),
                checkIn,
                checkOut,
                request.numGuests(),
                quote.nightCount(),
                quote.total(),
                quote.currency(),
                reservation.getStatus().name(),
                toNightlyDTOs(reservation),
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
     * Returns all reservations for the currently authenticated guest (newest first).
     */
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getMyReservations() {
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Guest guest = guestRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated guest not found: " + currentEmail));
        return reservationRepository.findByGuestIdOrderByCheckInDateDesc(guest.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 4: CANCEL — Guest cancels a booking
    // ─────────────────────────────────────────────────────────────

    /**
     * Cancels a reservation. Only CONFIRMED reservations can be cancelled.
     * Refundability is a property of the rate plan, not the status, so the status
     * simply becomes CANCELLED — which is exactly what the availability query
     * treats as "room freed".
     */
    @Transactional
    public void cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found with ID: " + reservationId));

        // Ensure the authenticated guest owns this reservation
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!reservation.getGuest().getEmail().equals(currentUsername)) {
            throw new IllegalArgumentException("You do not have permission to cancel this reservation.");
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalArgumentException(
                    "Only CONFIRMED reservations can be cancelled. Current status: " + reservation.getStatus());
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
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
        int nights = (int) ChronoUnit.DAYS.between(
                reservation.getCheckInDate(), reservation.getCheckOutDate());

        return new ReservationResponseDTO(
                reservation.getId(),
                reservation.getConfirmationNumber(),
                reservation.getStatus().name(),
                guest.getId(),
                guest.getFirstName() + " " + guest.getLastName(),
                ratePlan.getRoomType().getName(),
                assignedRoom != null ? assignedRoom.getId() : null,
                ratePlan.getName(),
                ratePlan.getCurrency(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                reservation.getNumGuests(),
                nights,
                reservation.getTotalPrice(),
                reservation.getCreatedAt()
        );
    }

    /**
     * Maps a reservation's frozen nights to their client-facing breakdown DTOs.
     */
    private List<NightlyPriceDTO> toNightlyDTOs(Reservation reservation) {
        return reservation.getNights().stream()
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .map(n -> new NightlyPriceDTO(
                        n.getDate(),
                        n.getRateAmount(),
                        n.getSource() == RateSource.PLAN ? "PLAN" : "BASE"))
                .toList();
    }
}
