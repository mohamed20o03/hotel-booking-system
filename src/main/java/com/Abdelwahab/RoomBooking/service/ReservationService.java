package com.Abdelwahab.RoomBooking.service;

import java.time.Duration;
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
import lombok.extern.slf4j.Slf4j;

/**
 * The heart of the booking domain: it coordinates the reservation lifecycle from
 * availability search through booking, check-in, cancellation, and hold expiry.
 *
 * <p><strong>State machine.</strong> This service owns the reservation status
 * transitions:
 * <ul>
 *   <li>{@code createBooking} opens a hold in {@code PENDING}.</li>
 *   <li>{@link PaymentService} settles the balance and moves it to
 *       {@code CONFIRMED} (that transition lives in the payment service, not here).</li>
 *   <li>{@link #checkIn} assigns a physical room and moves {@code CONFIRMED →
 *       CHECKED_IN}.</li>
 *   <li>{@link #cancelReservation} moves a live {@code PENDING} or {@code CONFIRMED}
 *       hold to {@code CANCELLED}.</li>
 *   <li>{@link #expireHold} moves a lapsed {@code PENDING} hold to {@code EXPIRED}.</li>
 * </ul>
 * A {@code NO_SHOW} status exists in the model for stays that never checked in.
 * Every terminal or hold-releasing transition returns the room-type inventory to
 * the allotment through {@link InventoryService}.
 *
 * <p><strong>Count-based inventory.</strong> A stay holds inventory by COUNT per
 * night rather than by pinning a physical room; a concrete room is chosen only at
 * check-in. This lets availability be evaluated per night without reserving a
 * specific room up front.
 *
 * <p><strong>Concurrency.</strong> {@link #createBooking} delegates the hold to
 * {@link InventoryService#reserve}, which takes a pessimistic write lock
 * ({@code SELECT ... FOR UPDATE}) on each night row so two guests racing for the
 * last room are serialized and the type can never be oversold. Separately,
 * {@link #expireHold} and {@link PaymentService} may both target the same
 * {@code PENDING} reservation; the reservation's {@code @Version} optimistic lock
 * makes the loser fail cleanly, and {@code expireHold} additionally re-checks the
 * status inside its own transaction so a payment that landed first is always
 * honoured.
 *
 * <p><strong>Price integrity.</strong> Booking freezes the per-night breakdown from
 * {@link PricingService} onto the reservation, so a later rate change never rewrites
 * a stay's total.
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton holding only
 * injected collaborators; correctness under concurrency comes from database locks
 * and the version column, not instance state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RatePlanRepository ratePlanRepository;
    private final GuestRepository guestRepository;
    private final ReservationRepository reservationRepository;
    private final PricingService pricingService;
    private final InventoryService inventoryService;

    // How long a new reservation holds its inventory before payment. If unpaid by
    // then, the expiry sweep frees the held nights. Kept short so abandoned carts
    // don't sit on scarce rooms.
    private static final Duration HOLD_DURATION = Duration.ofMinutes(30);

    // ─────────────────────────────────────────────────────────────
    // STEP 1: SEARCH — Guest looks for available options
    // ─────────────────────────────────────────────────────────────

    /**
     * Searches a hotel for every room type that can still be booked for the requested
     * dates, each paired with its rate plans priced day-by-day.
     *
     * <p>Availability is decided by the room-type inventory calendar (count per
     * night), not by pinning a physical room, and room types that cannot seat the
     * party or are sold out on any night are skipped. The base rate guarantees every
     * night has a price, so a stay no longer has to fit inside a single rate plan's
     * date window.
     *
     * <p>Read-only transactional: a pure query across inventory, rate plans, and
     * pricing, marked {@code readOnly} to skip dirty-checking overhead. Being a
     * non-locking read, its counts are indicative only — a subsequent booking still
     * locks the inventory before committing.
     *
     * @param hotelId      the hotel to search.
     * @param checkInDate  the first night of the stay (inclusive).
     * @param checkOutDate the checkout date (exclusive); assumed after
     *                     {@code checkInDate}.
     * @param numGuests    the party size; room types with a smaller max occupancy are
     *                     excluded.
     * @return one {@link AvailabilityResponseDTO} per bookable room type, each with
     *         its priced rate plans; an empty list if nothing is available.
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

            // Count-based availability across every night of the stay
            int availableCount = inventoryService.availableCount(
                    type.getId(), checkInDate, checkOutDate);

            if (availableCount <= 0) continue; // sold out or dates not open

            // Price every rate plan of this type for the requested dates
            List<RatePlanResponseDTO> ratePlanDTOs = ratePlanRepository.findByRoomTypeId(type.getId())
                    .stream()
                    .map(rp -> {
                        var quote = pricingService.quote(type, rp, checkInDate, checkOutDate);
                        return new RatePlanResponseDTO(
                                rp.getId(),
                                rp.getName(),
                                type.getCurrency(),
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
                    availableCount,
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
     * Opens a reservation hold under a single chosen rate plan, holding inventory in
     * the {@code PENDING} state pending payment.
     *
     * <p>The flow is:
     * <ol>
     *   <li>Resolve the authenticated guest (from the JWT, never the request body)
     *       and the chosen rate plan with its room type.</li>
     *   <li>Enforce occupancy and the plan's minimum-stay policy.</li>
     *   <li>Atomically reserve room-type inventory for every night — this takes the
     *       pessimistic write lock that prevents overselling.</li>
     *   <li>Price the stay day-by-day (plan overrides plus base-rate fallback).</li>
     *   <li>Persist the reservation with its frozen per-night breakdown and a hold
     *       expiry.</li>
     * </ol>
     *
     * <p>A physical room is NOT assigned here — the reservation holds count-based
     * inventory and remains {@code PENDING} with {@code assignedRoom == null} until
     * payment confirms it and, later, check-in pins a room. If unpaid by
     * {@code holdExpiresAt}, {@link HoldExpirySweeper} releases the hold.
     *
     * <p>Read-write transactional and atomic: the inventory lock is held until commit,
     * so any thrown exception rolls the whole booking back and leaves no partial hold.
     *
     * @param request the desired stay (rate plan id, dates, party size); must be
     *                non-{@code null} and valid per its DTO constraints.
     * @return a {@link ReservationConfirmationDTO} for the new {@code PENDING} hold,
     *         including the per-night breakdown and total.
     * @throws ResourceNotFoundException if the authenticated guest or the rate plan
     *         cannot be resolved.
     * @throws IllegalArgumentException  if the party exceeds the room type's max
     *         occupancy or the stay is shorter than the plan's minimum.
     * @throws NoAvailabilityException   if any night is closed or sold out (surfaced
     *         from {@link InventoryService#reserve}); rolls back the booking.
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

        // 5. Atomically hold one room per night (locks the nights; throws if sold out)
        inventoryService.reserve(roomType, checkIn, checkOut);

        // 6. Price the stay day-by-day
        PricingService.PriceQuote quote = pricingService.quote(roomType, ratePlan, checkIn, checkOut);

        // 7. Build the reservation — held as PENDING until paid. No physical room
        //    yet; that is assigned at check-in. The hold guarantees the inventory
        //    only until holdExpiresAt, after which the expiry sweep frees it.
        Reservation reservation = Reservation.builder()
                .guest(guest)
                .ratePlan(ratePlan)
                .assignedRoom(null)
                .confirmationNumber(generateConfirmationNumber())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .numGuests(request.numGuests())
                .totalPrice(quote.total())
                .status(ReservationStatus.PENDING)
                .holdExpiresAt(LocalDateTime.now().plus(HOLD_DURATION))
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

        log.info("Booking created [confirmation={} roomType={} nights={} total={} {}]",
                reservation.getConfirmationNumber(), roomType.getName(),
                quote.nightCount(), quote.total(), quote.currency());

        // 9. Rich confirmation with the breakdown (assignedRoomId is null until check-in)
        return new ReservationConfirmationDTO(
                reservation.getId(),
                reservation.getConfirmationNumber(),
                guest.getFirstName() + " " + guest.getLastName(),
                roomType.getName(),
                null,
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
    // STEP 2b: CHECK-IN — Assign a physical room on arrival
    // ─────────────────────────────────────────────────────────────

    /**
     * Checks a guest in: assigns a concrete physical room to a {@code CONFIRMED}
     * reservation and moves it to {@code CHECKED_IN}.
     *
     * <p>This is where a real room number is picked — from the rooms of the booked
     * type that are physically free (no overlapping {@code CHECKED_IN} stay, no
     * maintenance block) for the reservation's dates. A count-based hold guarantees a
     * room of the type was reserved, but a maintenance block or data drift can still
     * leave none physically assignable, which is treated as a conflict rather than a
     * not-found.
     *
     * <p>Read-write transactional: the room assignment and status change commit
     * together.
     *
     * @param reservationId the reservation to check in; must be in {@code CONFIRMED}
     *                      state.
     * @return a {@link ReservationResponseDTO} for the updated reservation, now
     *         carrying its assigned room.
     * @throws ResourceNotFoundException if no reservation exists with the given id.
     * @throws IllegalArgumentException  if the reservation is not {@code CONFIRMED}.
     * @throws NoAvailabilityException   if no physical room of the booked type is free
     *         to assign for the stay.
     */
    @Transactional
    public ReservationResponseDTO checkIn(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found with ID: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalArgumentException(
                    "Only CONFIRMED reservations can be checked in. Current status: " + reservation.getStatus());
        }

        RoomType roomType = reservation.getRatePlan().getRoomType();
        List<Room> freeRooms = roomRepository.findAvailableRooms(
                roomType.getId(), reservation.getCheckInDate(), reservation.getCheckOutDate());

        if (freeRooms.isEmpty()) {
            // Inventory count said a room was held, but no physical room is free —
            // indicates a maintenance block or data drift needing staff attention.
            log.warn("Check-in blocked: inventory held but no physical room free [confirmation={} roomTypeId={}]",
                    reservation.getConfirmationNumber(), roomType.getId());
            throw new NoAvailabilityException(
                    "No physical room is free to assign for this reservation. Please contact the front desk.");
        }

        reservation.setAssignedRoom(freeRooms.get(0));
        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservationRepository.save(reservation);

        log.info("Checked in [confirmation={} assignedRoomId={}]",
                reservation.getConfirmationNumber(), reservation.getAssignedRoom().getId());

        return toDTO(reservation);
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 3: LOOKUP — Guest retrieves their bookings
    // ─────────────────────────────────────────────────────────────

    /**
     * Looks up a single reservation by its human-facing confirmation number.
     *
     * <p>Used in "Manage My Booking" and check-in flows. The lookup is keyed by the
     * confirmation code rather than by ownership, so knowledge of the code is the
     * access token. Read-only transactional: a pure lookup performing no writes.
     *
     * @param confirmationNumber the opaque confirmation code issued at booking time.
     * @return a {@link ReservationResponseDTO} for the matching reservation.
     * @throws ResourceNotFoundException if no reservation carries that confirmation
     *         number.
     */
    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationByConfirmationNumber(String confirmationNumber) {
        Reservation reservation = reservationRepository.findByConfirmationNumber(confirmationNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No reservation found with confirmation number: " + confirmationNumber));
        return toDTO(reservation);
    }

    /**
     * Returns every reservation belonging to the currently authenticated guest,
     * newest stay first.
     *
     * <p><strong>Ownership-scoped:</strong> the guest is resolved from the security
     * context, not from a parameter, so a caller can only ever see their own bookings
     * (IDOR-safe by construction). Read-only transactional: a pure query.
     *
     * @return the caller's reservations as {@link ReservationResponseDTO} views; an
     *         empty list if they have none.
     * @throws ResourceNotFoundException if the authenticated guest cannot be resolved.
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
     * Cancels a live booking and releases its held inventory back to the allotment.
     *
     * <p><strong>Ownership-scoped:</strong> the authenticated guest must own the
     * reservation. Only {@code PENDING} (an unpaid hold) and {@code CONFIRMED} (a paid
     * booking) reservations hold inventory and are therefore cancellable; later states
     * are not. Refundability is a property of the rate plan, not the status, so the
     * status simply becomes {@code CANCELLED} and the freed nights become sellable
     * again.
     *
     * <p>Read-write transactional and atomic: the status change and the inventory
     * release commit together.
     *
     * @param reservationId the reservation to cancel.
     * @throws ResourceNotFoundException if no reservation exists with the given id.
     * @throws IllegalArgumentException  if the caller does not own the reservation, or
     *         its status forbids cancellation.
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

        // Both an unpaid hold (PENDING) and a paid booking (CONFIRMED) hold
        // inventory, so either can be cancelled; later states cannot.
        if (reservation.getStatus() != ReservationStatus.CONFIRMED
                && reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Only PENDING or CONFIRMED reservations can be cancelled. Current status: "
                            + reservation.getStatus());
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setHoldExpiresAt(null); // no longer a live hold
        reservationRepository.save(reservation);

        // Give the nights back to the allotment
        inventoryService.release(
                reservation.getRatePlan().getRoomType(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate());

        log.info("Reservation cancelled and inventory released [confirmation={}]",
                reservation.getConfirmationNumber());
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 5: EXPIRE — Release holds that were never paid
    // ─────────────────────────────────────────────────────────────

    /**
     * Expires a single lapsed hold: moves a {@code PENDING} reservation to
     * {@code EXPIRED} and releases its held nights back to the allotment.
     *
     * <p>Re-checks the status inside the transaction so a payment that landed between
     * the sweep's query and this call is respected — only a still-{@code PENDING}
     * reservation is expired; anything else is a no-op. The reservation's
     * {@code @Version} guard turns a payment committing concurrently on the same row
     * into an {@link org.springframework.dao.OptimisticLockingFailureException}, which
     * the caller ({@link HoldExpirySweeper}) catches so that one reservation is
     * skipped rather than the payment being clobbered.
     *
     * <p>Read-write transactional in its own (per-reservation) transaction, so one
     * failure or lock conflict does not roll back the rest of the sweep.
     *
     * @param reservationId the reservation to expire.
     */
    @Transactional
    public void expireHold(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING) {
            return; // already paid, cancelled, or gone — nothing to expire
        }

        reservation.setStatus(ReservationStatus.EXPIRED);
        reservation.setHoldExpiresAt(null);
        reservationRepository.save(reservation);

        inventoryService.release(
                reservation.getRatePlan().getRoomType(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate());

        log.info("Hold expired and inventory released [confirmation={}]",
                reservation.getConfirmationNumber());
    }

    /**
     * Returns the ids of every {@code PENDING} hold whose window has lapsed as of
     * {@code now}.
     *
     * <p>The sweeper expires each one through the proxied {@link #expireHold(Long)} so
     * each gets its own transaction; this method therefore returns ids rather than
     * entities, to avoid carrying detached state across those transaction boundaries.
     * Read-only transactional: a pure query.
     *
     * @param now the reference instant; holds with {@code holdExpiresAt} before this
     *            are considered lapsed. Must not be {@code null}.
     * @return the ids of lapsed pending holds; an empty list if none have lapsed.
     */
    @Transactional(readOnly = true)
    public List<Long> findLapsedHoldIds(LocalDateTime now) {
        return reservationRepository
                .findByStatusAndHoldExpiresAtBefore(ReservationStatus.PENDING, now)
                .stream()
                .map(Reservation::getId)
                .toList();
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
                ratePlan.getRoomType().getCurrency(),
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
