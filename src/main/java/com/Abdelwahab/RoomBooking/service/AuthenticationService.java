package com.Abdelwahab.RoomBooking.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.LoginRequestDTO;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;
import com.Abdelwahab.RoomBooking.security.JwtService;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates guest identity flows: self-registration and credential login,
 * each issuing a signed JWT the caller presents on subsequent requests.
 *
 * <p><strong>Responsibility.</strong> This service is the seam between the public
 * authentication endpoints and the security infrastructure. It composes
 * {@link GuestService} (account creation), Spring Security's
 * {@link AuthenticationManager} (credential verification), and
 * {@link com.Abdelwahab.RoomBooking.security.JwtService} (token minting). It owns
 * no persistence logic of its own beyond re-reading the freshly registered guest.
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton whose only fields
 * are injected collaborators; safe for concurrent use across request threads.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final GuestService guestService;
    private final GuestRepository guestRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Registers a new guest account and immediately issues an authentication token,
     * so a freshly registered guest is logged in without a second round-trip.
     *
     * <p>Delegates account creation (including uniqueness enforcement and password
     * hashing) to {@link GuestService#registerGuest}, then re-reads the persisted
     * guest to mint a token bound to its identity.
     *
     * @param request the new guest's profile and credentials; the email must not
     *                already be registered.
     * @return a signed JWT authenticating the newly created guest.
     * @throws com.Abdelwahab.RoomBooking.exception.DuplicateResourceException if the
     *         email is already in use (propagated from {@link GuestService}).
     * @throws RuntimeException if the guest cannot be re-read immediately after a
     *         successful registration (an unexpected consistency failure).
     */
    public String register(GuestRequestDTO request) {
        guestService.registerGuest(request);
        Guest guest = guestRepository.findByEmail(request.email())
            .orElseThrow(() -> new RuntimeException("User not found after registration"));

        String jwtToken = jwtService.generateTokens(guest);
        return jwtToken;
    }

    /**
     * Authenticates an existing guest by email and password and returns a fresh
     * authentication token.
     *
     * <p>Delegates credential verification to the {@link AuthenticationManager};
     * only if authentication succeeds is the guest re-read and a token minted. A bad
     * email or password fails inside {@code authenticate} before any token is issued.
     *
     * @param request the login credentials; email and password must match a stored
     *                guest.
     * @return a signed JWT authenticating the guest.
     * @throws org.springframework.security.core.AuthenticationException if the
     *         credentials are invalid (raised by the authentication manager).
     */
    public String login(LoginRequestDTO request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.email(),
                request.password()
            )
        );

        Guest guest = guestRepository.findByEmail(request.email())
            .orElseThrow(); // Should exist since authentication passed

        String jwtToken = jwtService.generateTokens(guest);
        return jwtToken;
    }
}
