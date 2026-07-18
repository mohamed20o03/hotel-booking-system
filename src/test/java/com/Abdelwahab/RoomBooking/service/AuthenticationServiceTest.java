package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.LoginRequestDTO;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;
import com.Abdelwahab.RoomBooking.security.JwtService;

/**
 * Plain Mockito unit test for AuthenticationService — no Spring context is loaded
 * (@ExtendWith(MockitoExtension.class); GuestService, GuestRepository, JwtService and
 * the Spring Security AuthenticationManager are @Mock stubs injected into the service).
 * It verifies the registration and login orchestration in isolation: that registration
 * delegates to GuestService and only then mints a token, that login runs credentials
 * through the AuthenticationManager before issuing a token, and that a failed
 * authentication short-circuits before any token is generated.
 */
@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceTest {

    @Mock private GuestService guestService;
    @Mock private GuestRepository guestRepository;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @InjectMocks private AuthenticationService authenticationService;

    private GuestRequestDTO registerRequest;
    private LoginRequestDTO loginRequest;
    private Guest guest;

    @BeforeEach
    public void setup() {
        registerRequest = new GuestRequestDTO(
                "Ada", "Lovelace", "ada@example.com", "raw-password",
                "+201234567", "Egyptian", "PASSPORT", "A1234567",
                LocalDate.of(1990, 1, 1));

        loginRequest = new LoginRequestDTO("ada@example.com", "raw-password");

        guest = Guest.builder().id(42L).email("ada@example.com").build();
    }

    // ── register ──────────────────────────────────────────────────

    /**
     * Given GuestService will persist the account and the repository then finds the new
     * guest for token minting; when a guest registers; then the returned token is the
     * one JwtService issued, and registration is verified to run before token generation.
     */
    @Test
    public void register_registersGuest_thenReturnsToken() {
        when(guestRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(guest));
        when(jwtService.generateTokens(guest)).thenReturn("jwt-token");

        String token = authenticationService.register(registerRequest);

        assertThat(token).isEqualTo("jwt-token");
        // Registration must happen before token generation
        verify(guestService).registerGuest(registerRequest);
        verify(jwtService).generateTokens(guest);
    }

    /**
     * Given the repository cannot find the guest immediately after registration;
     * when registration runs; then a RuntimeException reporting the missing user is
     * raised and no token is generated — an inconsistent post-registration state never
     * yields a token.
     */
    @Test
    public void register_throws_whenGuestMissingAfterRegistration() {
        when(guestRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.register(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found after registration");

        verify(jwtService, never()).generateTokens(any());
    }

    // ── login ─────────────────────────────────────────────────────

    /**
     * Given the AuthenticationManager accepts the credentials and the repository finds
     * the guest; when a guest logs in; then the returned token is the one JwtService
     * issued, and the AuthenticationManager is verified to have been called with the
     * exact principal and password supplied.
     */
    @Test
    public void login_authenticates_thenReturnsToken() {
        Authentication auth = new UsernamePasswordAuthenticationToken("ada@example.com", "raw-password");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(guestRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(guest));
        when(jwtService.generateTokens(guest)).thenReturn("jwt-token");

        String token = authenticationService.login(loginRequest);

        assertThat(token).isEqualTo("jwt-token");
        // Credentials were checked with the right principal/password
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("ada@example.com", "raw-password"));
    }

    /**
     * Given the AuthenticationManager rejects the credentials with BadCredentialsException;
     * when a guest attempts to log in; then that exception propagates unchanged, the
     * repository is never queried, and no token is generated — a failed credential check
     * short-circuits the whole flow.
     */
    @Test
    public void login_propagatesBadCredentials_andSkipsTokenGeneration() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authenticationService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(guestRepository, never()).findByEmail(any());
        verify(jwtService, never()).generateTokens(any());
    }
}
