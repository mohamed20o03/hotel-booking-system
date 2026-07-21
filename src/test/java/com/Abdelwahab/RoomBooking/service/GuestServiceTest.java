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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.Abdelwahab.RoomBooking.dto.ChangePasswordRequestDTO;
import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.GuestResponseDTO;
import com.Abdelwahab.RoomBooking.exception.DuplicateResourceException;
import com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;

/**
 * Plain Mockito unit test for GuestService — no Spring context is loaded
 * (@ExtendWith(MockitoExtension.class); the GuestRepository and PasswordEncoder
 * collaborators are @Mock stubs injected into the service under test). It exercises
 * the guest-registration, lookup, password-change, and ban logic in isolation from
 * persistence and HTTP.
 */
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

    /**
     * Given the email is free, the encoder hashes the raw password, and the repository
     * assigns an id on save; when a guest self-registers; then the returned DTO carries
     * the persisted id and the STANDARD loyalty tier, the captured entity stores the
     * hashed password rather than the raw one, and the role is pinned to ROLE_USER so
     * self-registration can never mint an admin.
     */
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

    /**
     * Given the repository reports the email already exists;
     * when a guest attempts to register with it; then a DuplicateResourceException is
     * raised and no save is attempted — the uniqueness guard runs before persistence.
     */
    @Test
    public void registerGuest_rejectsDuplicateEmail() {
        when(guestRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> guestService.registerGuest(sampleRequest))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("Email is already in use");

        verify(guestRepository, never()).save(any());
    }

    /**
     * Given the repository returns a guest for the id;
     * when the service looks it up; then the mapped DTO carries the expected id and
     * first name and the repository is queried exactly once.
     */
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

    /**
     * Given the repository returns empty for an unknown id;
     * when the service looks it up; then a ResourceNotFoundException naming the id is
     * raised.
     */
    @Test
    public void getGuestById_WhenGuestNotFound() {
        Long guestId = 99L;
        when(guestRepository.findById(guestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guestService.getGuestById(guestId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Guest not found with ID: " + guestId);
    }

    // ── changePassword ────────────────────────────────────────────

    /**
     * Given the correct current password and a new password;
     * when changePassword runs; then the new BCrypt hash is saved and the guest's
     * numeric ID is returned for the caller to trigger token revocation.
     */
    @Test
    public void changePassword_encodesNewPassword_andReturnsGuestId() {
        ChangePasswordRequestDTO req = new ChangePasswordRequestDTO("old-pass", "new-pass-123");
        when(guestRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(savedGuest));
        when(passwordEncoder.matches("old-pass", "hash_password")).thenReturn(true);
        when(passwordEncoder.encode("new-pass-123")).thenReturn("new-hash");
        when(guestRepository.save(any(Guest.class))).thenReturn(savedGuest);

        Long returnedId = guestService.changePassword("ada@example.com", req);

        assertThat(returnedId).isEqualTo(42L);
        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        // New hash must be stored, never the plain-text new password.
        assertThat(captor.getValue().getPassword()).isEqualTo("new-hash");
    }

    /**
     * Given an incorrect current password;
     * when changePassword runs; then BadCredentialsException is thrown and
     * no save is attempted — a stolen cookie alone cannot change credentials.
     */
    @Test
    public void changePassword_throwsBadCredentials_whenCurrentPasswordWrong() {
        ChangePasswordRequestDTO req = new ChangePasswordRequestDTO("wrong-pass", "new-pass-123");
        when(guestRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(savedGuest));
        when(passwordEncoder.matches("wrong-pass", "hash_password")).thenReturn(false);

        assertThatThrownBy(() -> guestService.changePassword("ada@example.com", req))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("Current password is incorrect");

        verify(guestRepository, never()).save(any());
    }

    /**
     * Given an email that has no matching guest;
     * when changePassword runs; then ResourceNotFoundException is raised
     * and no save is attempted.
     */
    @Test
    public void changePassword_throwsNotFound_whenGuestMissing() {
        ChangePasswordRequestDTO req = new ChangePasswordRequestDTO("any", "new-pass-123");
        when(guestRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guestService.changePassword("unknown@example.com", req))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(guestRepository, never()).save(any());
    }

    // ── banGuestById ─────────────────────────────────────────────

    /**
     * Given an existing guest;
     * when banGuestById runs; then {@code guest.banned} is set to {@code true} and
     * the entity is saved — subsequent logins will be rejected by
     * {@code isAccountNonLocked()}.
     */
    @Test
    public void banGuestById_setsBannedTrue_andSaves() {
        when(guestRepository.findById(42L)).thenReturn(Optional.of(savedGuest));
        when(guestRepository.save(any(Guest.class))).thenReturn(savedGuest);

        guestService.banGuestById(42L);

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        assertThat(captor.getValue().isBanned()).isTrue();
    }

    /**
     * Given a userId that matches no guest;
     * when banGuestById runs; then ResourceNotFoundException is thrown
     * and no save is attempted.
     */
    @Test
    public void banGuestById_throwsNotFound_whenGuestMissing() {
        when(guestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guestService.banGuestById(99L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Guest not found with ID: 99");

        verify(guestRepository, never()).save(any());
    }
}
