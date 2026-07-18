package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.Abdelwahab.RoomBooking.dto.AvailabilityResponseDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationConfirmationDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationRequestDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationResponseDTO;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.model.RatePlan;
import com.Abdelwahab.RoomBooking.model.RateSource;
import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.ReservationStatus;
import com.Abdelwahab.RoomBooking.model.Room;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;
import com.Abdelwahab.RoomBooking.repository.RatePlanRepository;
import com.Abdelwahab.RoomBooking.repository.ReservationRepository;
import com.Abdelwahab.RoomBooking.repository.RoomRepository;
import com.Abdelwahab.RoomBooking.repository.RoomTypeRepository;

/**
 * Plain Mockito unit test for ReservationService — no Spring context is loaded
 * (@ExtendWith(MockitoExtension.class); every collaborator is a @Mock, and the Spring
 * Security SecurityContext/Authentication are stubbed by hand via SecurityContextHolder
 * to stand in for the authenticated caller, cleared after each test). It verifies the
 * booking lifecycle in isolation from persistence, pricing arithmetic (delegated to a
 * mocked PricingService) and inventory locking (delegated to a mocked InventoryService):
 * availability search assembles room-type options with quoted rate plans; createBooking
 * opens a PENDING hold with a future expiry, holds inventory atomically and assigns no
 * physical room; cancel and expiry release the held inventory and move the status
 * correctly (including the no-op guard when a race already confirmed the reservation);
 * and check-in assigns a room and advances the status.
 */
@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RatePlanRepository ratePlanRepository;
    @Mock private GuestRepository guestRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PricingService pricingService;
    @Mock private InventoryService inventoryService;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks
    private ReservationService reservationService;

    private Guest sampleGuest;
    private Hotel sampleHotel;
    private RoomType sampleRoomType;
    private RatePlan sampleRatePlan;
    private Room sampleRoom;
    private Reservation sampleReservation;

    @BeforeEach
    public void setup() {
        sampleGuest = Guest.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .build();

        sampleHotel = Hotel.builder().id(100L).name("Test Hotel").build();

        sampleRoomType = RoomType.builder()
                .id(200L)
                .hotel(sampleHotel)
                .name("Deluxe Room")
                .maxOccupancy(2)
                .basePricePerNight(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        // Rate plans are now policy-only; per-night prices come from PricingService,
        // and currency comes from the room type.
        sampleRatePlan = RatePlan.builder()
                .id(300L)
                .roomType(sampleRoomType)
                .name("Standard Rate")
                .minStayNights(1)
                .isRefundable(true)
                .build();

        sampleRoom = Room.builder()
                .id(400L)
                .roomType(sampleRoomType)
                .roomNumber(101)
                .build();

        sampleReservation = Reservation.builder()
                .id(500L)
                .guest(sampleGuest)
                .ratePlan(sampleRatePlan)
                .assignedRoom(sampleRoom)
                .confirmationNumber("CONF123")
                .status(ReservationStatus.CONFIRMED)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(3))
                .numGuests(2)
                .totalPrice(new BigDecimal("300.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void mockSecurityContext(String email) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        SecurityContextHolder.setContext(securityContext);
    }

    /** Builds a two-night quote totalling 300 USD, both nights at the base rate. */
    private PricingService.PriceQuote twoNightQuote(LocalDate checkIn) {
        return new PricingService.PriceQuote("USD", new BigDecimal("300.00"), List.of(
                new PricingService.NightlyPrice(checkIn, new BigDecimal("150.00"), RateSource.BASE),
                new PricingService.NightlyPrice(checkIn.plusDays(1), new BigDecimal("150.00"), RateSource.BASE)));
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Given the repository finds a reservation by its confirmation number;
     * when it is looked up; then the mapped DTO carries that confirmation number and the
     * guest's full name.
     */
    @Test
    public void getReservationByConfirmationNumber_Success() {
        when(reservationRepository.findByConfirmationNumber("CONF123"))
            .thenReturn(Optional.of(sampleReservation));

        ReservationResponseDTO response = reservationService.getReservationByConfirmationNumber("CONF123");

        assertThat(response).isNotNull();
        assertThat(response.confirmationNumber()).isEqualTo("CONF123");
        assertThat(response.guestName()).isEqualTo("John Doe");
        verify(reservationRepository, times(1)).findByConfirmationNumber("CONF123");
    }

    /**
     * Given a hotel with one room type that has 4 rooms available for the stay and one
     * rate plan quoted at 300 for two nights; when availability is searched;
     * then a single option is returned carrying the room-type name, the available count,
     * and its rate plan with the quoted total price.
     */
    @Test
    public void searchAvailableOptions_ReturnsAvailableRooms() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);

        when(roomTypeRepository.findByHotelId(100L)).thenReturn(List.of(sampleRoomType));
        when(inventoryService.availableCount(200L, checkIn, checkOut)).thenReturn(4);
        when(ratePlanRepository.findByRoomTypeId(200L)).thenReturn(List.of(sampleRatePlan));
        when(pricingService.quote(any(RoomType.class), any(RatePlan.class), eq(checkIn), eq(checkOut)))
                .thenReturn(twoNightQuote(checkIn));

        List<AvailabilityResponseDTO> results = reservationService.searchAvailableOptions(100L, checkIn, checkOut, 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).roomTypeName()).isEqualTo("Deluxe Room");
        assertThat(results.get(0).availableRoomsCount()).isEqualTo(4);
        assertThat(results.get(0).ratePlans()).hasSize(1);
        assertThat(results.get(0).ratePlans().get(0).name()).isEqualTo("Standard Rate");
        assertThat(results.get(0).ratePlans().get(0).totalPrice()).isEqualByComparingTo("300.00");
    }

    /**
     * Given an authenticated guest, a resolvable rate plan, and a two-night quote of 300;
     * when a booking is created; then the confirmation reports the guest, the 300 total
     * over two nights with a per-night breakdown, and a PENDING status with no assigned
     * room; inventory is reserved atomically for the stay; and the persisted reservation
     * is PENDING with a future hold-expiry timestamp — booking opens a timed hold, not a
     * confirmed stay.
     */
    @Test
    public void createBooking_Success() {
        mockSecurityContext("john@example.com");

        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);
        ReservationRequestDTO request = new ReservationRequestDTO(300L, checkIn, checkOut, 2);

        when(guestRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleGuest));
        when(ratePlanRepository.findById(300L)).thenReturn(Optional.of(sampleRatePlan));
        when(pricingService.quote(any(RoomType.class), any(RatePlan.class), eq(checkIn), eq(checkOut)))
                .thenReturn(twoNightQuote(checkIn));

        // Mock save to just return the object passed into it
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArguments()[0]);

        ReservationConfirmationDTO confirmation = reservationService.createBooking(request);

        assertThat(confirmation).isNotNull();
        assertThat(confirmation.guestName()).isEqualTo("John Doe");
        assertThat(confirmation.totalPrice()).isEqualByComparingTo("300.00"); // 2 nights
        assertThat(confirmation.nights()).isEqualTo(2);
        assertThat(confirmation.nightlyBreakdown()).hasSize(2);
        // Booking now starts as a PENDING hold, awaiting payment to confirm
        assertThat(confirmation.status()).isEqualTo("PENDING");
        // No physical room is assigned at booking — that happens at check-in
        assertThat(confirmation.assignedRoomId()).isNull();

        // Inventory was held atomically for the stay
        verify(inventoryService, times(1)).reserve(sampleRoomType, checkIn, checkOut);

        // The held reservation carries a future hold-expiry timestamp
        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(saved.getValue().getHoldExpiresAt()).isAfter(LocalDateTime.now());
    }

    /**
     * Given the authenticated owner and a CONFIRMED reservation;
     * when it is cancelled; then the status moves to CANCELLED, the reservation is saved,
     * and the held inventory is released back to the allotment for the stay.
     */
    @Test
    public void cancelReservation_Success() {
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));

        reservationService.cancelReservation(500L);

        // Refundability is a rate-plan property, not a status: cancel -> CANCELLED
        assertThat(sampleReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationRepository, times(1)).save(sampleReservation);
        // Held inventory is returned to the allotment
        verify(inventoryService, times(1)).release(
                sampleRoomType,
                sampleReservation.getCheckInDate(),
                sampleReservation.getCheckOutDate());
    }

    /**
     * Given the owner and a PENDING reservation with a live future hold;
     * when it is cancelled; then the status moves to CANCELLED, the hold expiry is
     * cleared, and the held inventory is released — a PENDING hold reserves inventory too
     * and so must be cancellable.
     */
    @Test
    public void cancelReservation_alsoCancelsAPendingHold() {
        // A PENDING hold holds inventory too, so it must be cancellable.
        sampleReservation.setStatus(ReservationStatus.PENDING);
        sampleReservation.setHoldExpiresAt(LocalDateTime.now().plusMinutes(30));
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));

        reservationService.cancelReservation(500L);

        assertThat(sampleReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(sampleReservation.getHoldExpiresAt()).isNull(); // no longer a live hold
        verify(inventoryService, times(1)).release(
                sampleRoomType,
                sampleReservation.getCheckInDate(),
                sampleReservation.getCheckOutDate());
    }

    /**
     * Given a PENDING reservation whose hold expired a minute ago;
     * when the expiry sweep runs for it; then the status moves to EXPIRED, the hold
     * expiry is cleared, the reservation is saved, and the held inventory is released.
     */
    @Test
    public void expireHold_expiresPendingHold_andReleasesInventory() {
        sampleReservation.setStatus(ReservationStatus.PENDING);
        sampleReservation.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));

        reservationService.expireHold(500L);

        assertThat(sampleReservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(sampleReservation.getHoldExpiresAt()).isNull();
        verify(reservationRepository, times(1)).save(sampleReservation);
        verify(inventoryService, times(1)).release(
                sampleRoomType,
                sampleReservation.getCheckInDate(),
                sampleReservation.getCheckOutDate());
    }

    /**
     * Given a reservation that a payment confirmed between the sweep's query and this
     * call (now CONFIRMED); when expiry runs for it; then it stays CONFIRMED, is not
     * saved, and inventory is not released — the guard prevents a race from expiring a
     * paid booking.
     */
    @Test
    public void expireHold_isNoOp_whenReservationAlreadyConfirmed() {
        // A payment confirmed the reservation between the sweep's query and this
        // call — it must not be expired, and inventory must not be released.
        sampleReservation.setStatus(ReservationStatus.CONFIRMED);
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));

        reservationService.expireHold(500L);

        assertThat(sampleReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(inventoryService, never()).release(any(RoomType.class), any(LocalDate.class), any(LocalDate.class));
    }

    /**
     * Given a reservation and an available physical room of its type for the stay;
     * when the front desk checks it in; then the status moves to CHECKED_IN, the room is
     * assigned to the reservation, and the response reports the assigned room id — the
     * physical room is bound at check-in, not at booking.
     */
    @Test
    public void checkIn_AssignsRoom_AndMovesToCheckedIn() {
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));
        when(roomRepository.findAvailableRooms(
                200L, sampleReservation.getCheckInDate(), sampleReservation.getCheckOutDate()))
                .thenReturn(List.of(sampleRoom));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArguments()[0]);

        ReservationResponseDTO response = reservationService.checkIn(500L);

        assertThat(sampleReservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(sampleReservation.getAssignedRoom()).isEqualTo(sampleRoom);
        assertThat(response.assignedRoomId()).isEqualTo(sampleRoom.getId());
    }
}
