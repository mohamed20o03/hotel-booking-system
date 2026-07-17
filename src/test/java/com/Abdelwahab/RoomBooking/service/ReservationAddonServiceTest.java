package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.Abdelwahab.RoomBooking.dto.ReservationAddonRequestDTO;
import com.Abdelwahab.RoomBooking.dto.ReservationAddonResponseDTO;
import com.Abdelwahab.RoomBooking.model.Addon;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.model.RatePlan;
import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.ReservationAddon;
import com.Abdelwahab.RoomBooking.model.ReservationStatus;
import com.Abdelwahab.RoomBooking.model.RoomType;
import com.Abdelwahab.RoomBooking.repository.AddonRepository;
import com.Abdelwahab.RoomBooking.repository.ReservationAddonRepository;
import com.Abdelwahab.RoomBooking.repository.ReservationRepository;

/**
 * Unit test for ReservationAddonService. Covers the money-affecting behaviour:
 * the unit price is frozen from the catalogue (never the client), the line rolls
 * into the reservation total, add-ons can only change while PENDING, and both
 * ownership and cross-hotel guards hold.
 */
@ExtendWith(MockitoExtension.class)
public class ReservationAddonServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationAddonRepository reservationAddonRepository;
    @Mock private AddonRepository addonRepository;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks private ReservationAddonService service;

    private Hotel hotel;
    private RoomType roomType;
    private Reservation reservation;
    private Addon addon;

    @BeforeEach
    public void setup() {
        hotel = Hotel.builder().id(1L).build();
        roomType = RoomType.builder().id(2L).hotel(hotel).currency("EGP").build();
        RatePlan ratePlan = RatePlan.builder().id(3L).roomType(roomType).build();
        Guest guest = Guest.builder().id(4L).email("john@example.com").build();

        reservation = Reservation.builder()
                .id(500L)
                .guest(guest)
                .ratePlan(ratePlan)
                .status(ReservationStatus.PENDING)
                .totalPrice(new BigDecimal("300.00"))
                .build();

        addon = Addon.builder()
                .id(10L)
                .hotel(hotel)
                .name("Spa")
                .price(new BigDecimal("80.00"))
                .available(true)
                .build();
    }

    private void mockSecurityContext(String email) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void attachAddon_freezesCatalogPrice_andAddsToTotal() {
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));
        when(addonRepository.findById(10L)).thenReturn(Optional.of(addon));
        when(reservationAddonRepository.save(any(ReservationAddon.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        // quantity 2 x 80 = 160, added to the 300 base total.
        ReservationAddonResponseDTO dto = service.attachAddon(500L,
                new ReservationAddonRequestDTO(10L, 2));

        assertThat(dto.unitPrice()).isEqualByComparingTo("80.00");
        assertThat(dto.lineTotal()).isEqualByComparingTo("160.00");
        assertThat(reservation.getTotalPrice()).isEqualByComparingTo("460.00");
        verify(reservationRepository).save(reservation);
    }

    @Test
    public void attachAddon_rejectedWhenReservationNotPending() {
        reservation.setStatus(ReservationStatus.CONFIRMED);
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.attachAddon(500L, new ReservationAddonRequestDTO(10L, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only be changed while a reservation is PENDING");

        verify(reservationAddonRepository, never()).save(any(ReservationAddon.class));
    }

    @Test
    public void attachAddon_rejectedWhenGuestDoesNotOwnReservation() {
        mockSecurityContext("someone-else@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.attachAddon(500L, new ReservationAddonRequestDTO(10L, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permission");

        verify(reservationAddonRepository, never()).save(any(ReservationAddon.class));
    }

    @Test
    public void attachAddon_rejectedWhenAddonBelongsToAnotherHotel() {
        addon.setHotel(Hotel.builder().id(99L).build());
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));
        when(addonRepository.findById(10L)).thenReturn(Optional.of(addon));

        assertThatThrownBy(() -> service.attachAddon(500L, new ReservationAddonRequestDTO(10L, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different hotel");

        verify(reservationAddonRepository, never()).save(any(ReservationAddon.class));
    }

    @Test
    public void attachAddon_rejectedWhenAddonUnavailable() {
        addon.setAvailable(false);
        mockSecurityContext("john@example.com");
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));
        when(addonRepository.findById(10L)).thenReturn(Optional.of(addon));

        assertThatThrownBy(() -> service.attachAddon(500L, new ReservationAddonRequestDTO(10L, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not currently available");

        verify(reservationAddonRepository, never()).save(any(ReservationAddon.class));
    }

    @Test
    public void detachAddon_subtractsLineFromTotal_andDeletes() {
        mockSecurityContext("john@example.com");
        ReservationAddon line = ReservationAddon.builder()
                .id(70L)
                .reservation(reservation)
                .addon(addon)
                .quantity(2)
                .unitPrice(new BigDecimal("80.00"))
                .build();
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));
        when(reservationAddonRepository.findById(70L)).thenReturn(Optional.of(line));

        service.detachAddon(500L, 70L);

        // 300 - (2 x 80) = 140
        assertThat(reservation.getTotalPrice()).isEqualByComparingTo("140.00");
        verify(reservationAddonRepository).delete(line);
    }
}
