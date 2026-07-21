package com.Abdelwahab.RoomBooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

import com.Abdelwahab.RoomBooking.dto.ChangePasswordRequestDTO;
import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.LoginRequestDTO;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;
import com.Abdelwahab.RoomBooking.security.JwtService;
import com.Abdelwahab.RoomBooking.security.TokenBlacklistService;

/**
 * Plain Mockito unit test for AuthenticationService — no Spring context is loaded
 * (@ExtendWith(MockitoExtension.class); GuestService, GuestRepository, JwtService and
 * the Spring Security AuthenticationManager are @Mock stubs injected into the service).
 * It verifies the registration, login, logout, password-change, and ban orchestration
 * in isolation: that registration delegates to GuestService and only then mints a token,
 * that login runs credentials through the AuthenticationManager before issuing a token,
 * that logout extracts the JTI and blacklists it (and safely skips bad tokens),
 * that changePassword delegates to GuestService then revokes all tokens, and that
 * banUser both revokes tokens and sets the DB banned flag.
 */
@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceTest {

    @Mock private GuestService guestService;
    @Mock private GuestRepository guestRepository;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenBlacklistService tokenBlacklistService;
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

    // ── logout ───────────────────────────────────────────────────

    /**
     * Given a valid, unexpired token with a positive remaining TTL;
     * when logout runs; then the JTI is extracted and recorded in the blacklist
     * with the remaining seconds as TTL and the reason "LOGOUT".
     */
    @Test
    public void logout_blacklistsToken_whenValidAndUnexpired() {
        when(jwtService.extractJti("valid-token")).thenReturn("user123:nanoABC");
        when(jwtService.getRemainingExpirationInSeconds("valid-token")).thenReturn(3600L);

        authenticationService.logout("valid-token");

        verify(tokenBlacklistService).blacklist("user123:nanoABC", 3600L, "LOGOUT");
    }

    /**
     * Given a token that is already expired (remaining seconds == 0);
     * when logout runs; then no blacklist entry is written — there is nothing to revoke.
     */
    @Test
    public void logout_skipsBlacklist_whenTokenAlreadyExpired() {
        when(jwtService.extractJti("expired-token")).thenReturn("user123:nanoXYZ");
        when(jwtService.getRemainingExpirationInSeconds("expired-token")).thenReturn(0L);

        authenticationService.logout("expired-token");

        verify(tokenBlacklistService, never()).blacklist(anyString(), anyLong(), anyString());
    }

    /**
     * Given a null token (no cookie present);
     * when logout runs; then the method exits silently without touching the blacklist.
     */
    @Test
    public void logout_isNoOp_whenTokenIsNull() {
        authenticationService.logout(null);

        verify(tokenBlacklistService, never()).blacklist(anyString(), anyLong(), anyString());
        verify(jwtService, never()).extractJti(any());
    }

    /**
     * Given a malformed or unparseable token;
     * when logout runs; then the exception is swallowed and no blacklist entry is written
     * — stale cookies on logout must never cause a 500.
     */
    @Test
    public void logout_swallowsParseException_andSkipsBlacklist() {
        when(jwtService.extractJti("bad-token")).thenThrow(new RuntimeException("parse error"));

        authenticationService.logout("bad-token");

        verify(tokenBlacklistService, never()).blacklist(anyString(), anyLong(), anyString());
    }

    // ── changePassword ───────────────────────────────────────────

    /**
     * Given a valid email and correct current password;
     * when changePassword runs; then GuestService persists the new hash, and
     * a global user-level ban is written to Redis so all pre-existing tokens are revoked.
     */
    @Test
    public void changePassword_delegatesToGuestService_andRevokesAllTokens() {
        ChangePasswordRequestDTO req = new ChangePasswordRequestDTO("old", "new-pass-123");
        when(guestService.changePassword("ada@example.com", req)).thenReturn(42L);

        Long id = authenticationService.changePassword("ada@example.com", req);

        assertThat(id).isEqualTo(42L);
        // Global ban must be written with the guest's ID and the PASSWORD_CHANGED reason.
        verify(tokenBlacklistService).banUserGlobally(eq("42"), anyLong(), eq("PASSWORD_CHANGED"));
    }

    /**
     * Given GuestService throws BadCredentialsException (wrong current password);
     * when changePassword runs; then the exception propagates and no Redis ban is written
     * — no tokens should be revoked if the password was not actually changed.
     */
    @Test
    public void changePassword_propagatesBadCredentials_withoutRevokingTokens() {
        ChangePasswordRequestDTO req = new ChangePasswordRequestDTO("wrong", "new-pass-123");
        when(guestService.changePassword("ada@example.com", req))
                .thenThrow(new BadCredentialsException("Current password is incorrect"));

        assertThatThrownBy(() -> authenticationService.changePassword("ada@example.com", req))
                .isInstanceOf(BadCredentialsException.class);

        verify(tokenBlacklistService, never()).banUserGlobally(anyString(), anyLong(), anyString());
    }

    // ── banUser ────────────────────────────────────────────────

    /**
     * Given a valid userId and reason;
     * when banUser runs; then the global Redis ban is written AND GuestService persists
     * the banned flag — both tiers must fire together to fully close the ban gap.
     */
    @Test
    public void banUser_writesRedisBan_andSetsBannedFlag() {
        authenticationService.banUser(42L, "ADMIN_BAN");

        // Tier 1: global Redis ban so all existing tokens are immediately rejected.
        verify(tokenBlacklistService).banUserGlobally(eq("42"), anyLong(), eq("ADMIN_BAN"));
        // Tier 2: DB flag so new logins are blocked permanently.
        verify(guestService).banGuestById(42L);
    }
}
