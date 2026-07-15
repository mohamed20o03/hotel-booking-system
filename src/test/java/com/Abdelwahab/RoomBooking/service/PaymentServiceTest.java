package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.Abdelwahab.RoomBooking.dto.PaymentRequestDTO;
import com.Abdelwahab.RoomBooking.dto.PaymentResponseDTO;
import com.Abdelwahab.RoomBooking.exception.PaymentException;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.model.Payment;
import com.Abdelwahab.RoomBooking.model.RatePlan;
import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.ReservationStatus;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.repository.PaymentRepository;
import com.Abdelwahab.RoomBooking.repository.ReservationRepository;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks
    private PaymentService paymentService;

    private Guest sampleGuest;
    private RoomType sampleRoomType;
    private RatePlan sampleRatePlan;
    private Reservation sampleReservation;

    @BeforeEach
    public void setup() {
        sampleGuest = Guest.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .build();

        Hotel hotel = Hotel.builder().id(100L).name("Test Hotel").build();

        sampleRoomType = RoomType.builder()
                .id(200L)
                .hotel(hotel)
                .name("Deluxe Room")
                .maxOccupancy(2)
                .basePricePerNight(new BigDecimal("150.00"))
                .currency("USD")
                .build();

        sampleRatePlan = RatePlan.builder()
                .id(300L)
                .roomType(sampleRoomType)
                .name("Standard Rate")
                .minStayNights(1)
                .isRefundable(true)
                .build();

        sampleReservation = Reservation.builder()
                .id(500L)
                .guest(sampleGuest)
                .ratePlan(sampleRatePlan)
                .confirmationNumber("CONF123")
                .status(ReservationStatus.PENDING)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(30))
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(3))
                .numGuests(2)
                .totalPrice(new BigDecimal("300.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(String email) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    public void pay_fullAmount_confirmsReservation_andClearsHold() {
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));
        when(paymentRepository.sumSuccessfulPaymentsByReservationId(500L)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = (Payment) i.getArguments()[0];
            p.setId(900L);
            return p;
        });

        PaymentResponseDTO response = paymentService.pay(
                new PaymentRequestDTO(500L, new BigDecimal("300.00"), "VISA"));

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.amountPaid()).isEqualByComparingTo("300.00");
        assertThat(response.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.provider()).isEqualTo("FRONT_DESK");

        // Reservation flipped to CONFIRMED and the timed hold was cleared
        assertThat(sampleReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(sampleReservation.getHoldExpiresAt()).isNull();
        verify(reservationRepository, times(1)).save(sampleReservation);
    }

    @Test
    public void pay_partialAmount_leavesReservationPending_withBalanceDue() {
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));
        when(paymentRepository.sumSuccessfulPaymentsByReservationId(500L)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);

        PaymentResponseDTO response = paymentService.pay(
                new PaymentRequestDTO(500L, new BigDecimal("100.00"), "CASH"));

        assertThat(response.reservationStatus()).isEqualTo("PENDING");
        assertThat(response.amountPaid()).isEqualByComparingTo("100.00");
        assertThat(response.balanceDue()).isEqualByComparingTo("200.00");

        // Still an open hold — not confirmed, expiry not cleared
        assertThat(sampleReservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(sampleReservation.getHoldExpiresAt()).isNotNull();
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    public void pay_finalInstalment_confirmsOnceBalanceCleared() {
        // 200 already paid; this 100 clears the 300 total.
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));
        when(paymentRepository.sumSuccessfulPaymentsByReservationId(500L)).thenReturn(new BigDecimal("200.00"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);

        PaymentResponseDTO response = paymentService.pay(
                new PaymentRequestDTO(500L, new BigDecimal("100.00"), "VISA"));

        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.amountPaid()).isEqualByComparingTo("300.00");
        assertThat(response.balanceDue()).isEqualByComparingTo("0.00");
        assertThat(sampleReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    public void pay_rejectsAmountExceedingBalanceDue() {
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));
        when(paymentRepository.sumSuccessfulPaymentsByReservationId(500L)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> paymentService.pay(
                new PaymentRequestDTO(500L, new BigDecimal("500.00"), "VISA")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("exceeds the balance due");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    public void pay_rejectsNonPendingReservation() {
        sampleReservation.setStatus(ReservationStatus.CONFIRMED);
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));

        assertThatThrownBy(() -> paymentService.pay(
                new PaymentRequestDTO(500L, new BigDecimal("300.00"), "VISA")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("not awaiting payment");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    public void pay_rejectsExpiredHold() {
        sampleReservation.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));

        assertThatThrownBy(() -> paymentService.pay(
                new PaymentRequestDTO(500L, new BigDecimal("300.00"), "VISA")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("payment window");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    public void pay_rejectsWhenGuestDoesNotOwnReservation() {
        mockSecurityContext("someone-else@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(sampleReservation));

        assertThatThrownBy(() -> paymentService.pay(
                new PaymentRequestDTO(500L, new BigDecimal("300.00"), "VISA")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permission");

        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
