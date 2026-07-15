package com.Abdelwahab.RoomBooking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.GuestResponseDTO;
import com.Abdelwahab.RoomBooking.exception.DuplicateResourceException;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;

@ExtendWith(MockitoExtension.class)
public class GuestServiceTest {

    @Mock
    private GuestRepository guestRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private GuestService guestService;

    private GuestRequestDTO sampleRequest;
    private Guest savedGuest;

    @BeforeEach
    public void setup() {
        sampleRequest = new GuestRequestDTO(
            "Ada", "Lovelace", "ada@example.com", "raw-password",
            "+201234567", "Egyptian", "PASSPORT", "A1234567",
            LocalDate.of(1990, 1, 1));

        savedGuest = Guest.builder()
            .id(42L)
            .firstName("Ada")
            .lastName("Lovelace")
            .email("ada@example.com")
            .password("hash_password")
            .phone("+201234567")
            .nationality("Egyptian")
            .documentType("PASSPORT")
            .documentNumber("A1234567")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .loyaltyTier("STANDARD")
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    public void registerGuest_encodesPassword_andDefaultsLoyaltyTier() {
        when(guestRepository.existsByEmail(sampleRequest.email())).thenReturn(false);
        when(passwordEncoder.encode(sampleRequest.password())).thenReturn("hash-password");
        when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> {
            Guest g = inv.getArgument(0);
            g.setId(42L);
            return g;
        });

        GuestResponseDTO response = guestService.registerGuest(sampleRequest);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.loyaltyTier()).isEqualTo("STANDARD");

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword())
            .isNotEqualTo("raw-password")
            .isEqualTo("hash-password");
        // Self-registration must never mint an admin — role is pinned to ROLE_USER.
        assertThat(captor.getValue().getRole()).isEqualTo("ROLE_USER");
    }

    @Test
    public void registerGuest_rejectsDuplicateEmail() {
        when(guestRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> guestService.registerGuest(sampleRequest))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("Email is already in use");

        verify(guestRepository, never()).save(any());
    }

    @Test
    public void getGuestById_Success(){
        Long guestId = 42L;
        when(guestRepository.findById(guestId)).thenReturn(Optional.of(savedGuest));

        GuestResponseDTO response = guestService.getGuestById(guestId);

        assertThat(response).isNotNull();
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.id()).isEqualTo(guestId);

        verify(guestRepository, times(1)).findById(guestId);
    }

    @Test
    public void getGuestById_WhenGuestNotFound() {
        Long guestId = 99L;
        when(guestRepository.findById(guestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guestService.getGuestById(guestId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Guest not found with ID: " + guestId);

    }
    
}
