package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RatePlanRepository ratePlanRepository;
    @Mock private GuestRepository guestRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PricingService pricingService;
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

        // Rate plans are now policy-only; per-night prices come from PricingService.
        sampleRatePlan = RatePlan.builder()
                .id(300L)
                .roomType(sampleRoomType)
                .name("Standard Rate")
                .currency("USD")
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

    @Test
    public void searchAvailableOptions_ReturnsAvailableRooms() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);

        when(roomTypeRepository.findByHotelId(100L)).thenReturn(List.of(sampleRoomType));
        when(roomRepository.findAvailableRooms(200L, checkIn, checkOut)).thenReturn(List.of(sampleRoom));
        when(ratePlanRepository.findByRoomTypeId(200L)).thenReturn(List.of(sampleRatePlan));
        when(pricingService.quote(any(RoomType.class), any(RatePlan.class), eq(checkIn), eq(checkOut)))
                .thenReturn(twoNightQuote(checkIn));

        List<AvailabilityResponseDTO> results = reservationService.searchAvailableOptions(100L, checkIn, checkOut, 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).roomTypeName()).isEqualTo("Deluxe Room");
        assertThat(results.get(0).availableRoomsCount()).isEqualTo(1);
        assertThat(results.get(0).ratePlans()).hasSize(1);
        assertThat(results.get(0).ratePlans().get(0).name()).isEqualTo("Standard Rate");
        assertThat(results.get(0).ratePlans().get(0).totalPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    public void createBooking_Success() {
        mockSecurityContext("john@example.com");

        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);
        ReservationRequestDTO request = new ReservationRequestDTO(300L, checkIn, checkOut, 2);

        when(guestRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleGuest));
        when(ratePlanRepository.findById(300L)).thenReturn(Optional.of(sampleRatePlan));
        when(roomRepository.findAvailableRooms(200L, checkIn, checkOut)).thenReturn(List.of(sampleRoom));
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
        assertThat(confirmation.status()).isEqualTo("CONFIRMED");

        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }

    @Test
    public void cancelReservation_Success() {
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));

        reservationService.cancelReservation(500L);

        // Refundability is a rate-plan property, not a status: cancel -> CANCELLED
        assertThat(sampleReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationRepository, times(1)).save(sampleReservation);
    }
}
