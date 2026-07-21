package com.Abdelwahab.RoomBooking.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import com.Abdelwahab.RoomBooking.dto.ChangePasswordRequestDTO;
import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.LoginRequestDTO;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;
import com.Abdelwahab.RoomBooking.security.JwtService;
import com.Abdelwahab.RoomBooking.security.TokenBlacklistService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class AuthenticationService {

    private final GuestService guestService;
    private final GuestRepository guestRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Upper bound, in seconds, on how long any issued JWT can remain valid
     * ({@code 86400s} = 24h, matching {@code jwt.expiration}). A global user-ban
     * entry is given exactly this TTL: once it elapses, every token that existed at
     * ban time has itself expired, so the ban key has done its job and Redis can
     * reclaim it. Sizing the TTL to the token lifetime — rather than leaving it
     * open-ended — keeps the revocation keyspace bounded with no cleanup job.
     */
    private static final long MAX_TOKEN_LIFESPAN_SECONDS = 86400L;

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

    /**
     * Globally revokes all active tokens for a guest across all devices in a single Redis write.
     * Serves as the implementation for "logout everywhere", security resets, and administrative bans.
     *
     * <p><strong>How it works.</strong> Instead of tracking and blacklisting individual tokens,
     * this records a single global ban key ({@code blacklist:user:<userId>}) via
     * {@link TokenBlacklistService#banUserGlobally}. On subsequent requests, {@code JwtAuthenticationFilter}
     * extracts the {@code <userId>} prefix from the compound {@code jti} and halts execution with
     * {@code 401 Unauthorized} if this global key exists.
     *
     * <p><strong>TTL strategy.</strong> The ban key is stored with a TTL equal to the maximum possible
     * token lifespan ({@value #MAX_TOKEN_LIFESPAN_SECONDS} seconds). Once this window elapses, all
     * tokens minted prior to the ban will have naturally expired, allowing Redis to auto-evict the key
     * without unbounded keyspace growth or scheduled clean-up jobs.
     *
     * @param userId the target guest identifier; must match the prefix embedded in their compound {@code jti}.
     * @param reason the audit-ready revocation cause (e.g., {@code "ADMIN_BAN"}, {@code "PASSWORD_CHANGED"},
     *               {@code "LOGOUT_ALL_DEVICES"}).
     */
    public void logoutFromAllDevices(String userId, String reason) {
        tokenBlacklistService.banUserGlobally(userId, MAX_TOKEN_LIFESPAN_SECONDS, reason);
    }

    /**
     * Revokes a single token by its raw JWT string, intended for single-device logout.
     *
     * <p>Extracts the compound {@code jti} and the remaining TTL from the token, then
     * writes a token-level blacklist entry ({@code blacklist:token:<jti>}) so that any
     * copy of the token captured before logout is rejected by
     * {@link com.Abdelwahab.RoomBooking.security.JwtAuthenticationFilter} on its next
     * use. If the token is already expired or malformed the step is silently skipped —
     * there is nothing to revoke.
     *
     * @param rawToken the raw JWT string extracted from the {@code jwt} cookie;
     *                 {@code null} is accepted and treated as a no-op
     */
    public void logout(String rawToken) {
        if (rawToken == null) return;
        try {
            String jti = jwtService.extractJti(rawToken);
            long remainingSeconds = jwtService.getRemainingExpirationInSeconds(rawToken);
            if (jti != null && remainingSeconds > 0) {
                tokenBlacklistService.blacklist(jti, remainingSeconds, "LOGOUT");
            }
        } catch (Exception e) {
            // Token is already expired or malformed — blacklisting is unnecessary.
            // Log at DEBUG; this is expected when a client sends a stale cookie.
            log.debug("Skipping blacklist on logout: token unparseable or expired. {}", e.getMessage());
        }
    }

    /**
     * Changes the authenticated guest's password and immediately revokes all their
     * active tokens across every device.
     *
     * <p>Delegates credential re-verification and the password write to
     * {@link GuestService#changePassword}; on success calls
     * {@link #logoutFromAllDevices} so every pre-existing token is invalidated
     * server-side with the reason {@code "PASSWORD_CHANGED"}.
     *
     * <p>The controller is responsible only for clearing the current device's cookie
     * after this method returns.
     *
     * @param email   the authenticated guest's email (sourced from the security context)
     * @param request the current and new passwords
     * @return the guest's numeric ID, passed back so the controller can log or respond
     * @throws org.springframework.security.authentication.BadCredentialsException if
     *         {@code currentPassword} does not match the stored hash
     */
    public Long changePassword(String email, ChangePasswordRequestDTO request) {
        Long guestId = guestService.changePassword(email, request);
        logoutFromAllDevices(String.valueOf(guestId), "PASSWORD_CHANGED");
        log.info("Password changed and all tokens revoked [guestId={}]", guestId);
        return guestId;
    }

    /**
     * Bans a guest globally by combining two independent revocation tiers:
     *
     * <ol>
     *   <li><strong>Immediate token revocation (Redis).</strong> Calls
     *       {@link #logoutFromAllDevices} to write {@code blacklist:user:<userId>} with
     *       a TTL equal to the maximum JWT lifespan. Every pre-existing token is rejected
     *       on its next request, across all devices, within milliseconds.</li>
     *   <li><strong>Permanent login block (DB).</strong> Calls
     *       {@link GuestService#banGuestById} to set {@code guest.banned = true}.
     *       Spring Security's {@link DaoAuthenticationProvider} then throws
     *       {@link org.springframework.security.authentication.LockedException} on any
     *       subsequent login attempt, regardless of credential validity. This persists
     *       beyond the Redis TTL and closes the gap where a banned guest could
     *       re-authenticate after the ban key expires.</li>
     * </ol>
     *
     * @param userId the target guest's numeric ID
     * @param reason machine-readable audit cause (e.g. {@code "ADMIN_BAN"},
     *               {@code "TERMS_VIOLATION"})
     */
    public void banUser(Long userId, String reason) {
        logoutFromAllDevices(String.valueOf(userId), reason);
        guestService.banGuestById(userId);
        log.info("Admin banned user [userId={}] reason={}", userId, reason);
    }
}
