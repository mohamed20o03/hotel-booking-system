    package com.Abdelwahab.RoomBooking.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Abdelwahab.RoomBooking.dto.GuestRequestDTO;
import com.Abdelwahab.RoomBooking.dto.GuestResponseDTO;
import com.Abdelwahab.RoomBooking.exception.DuplicateResourceException;
import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.repository.GuestRepository;

import lombok.RequiredArgsConstructor;

/**
 * Manages guest accounts: self-service registration and profile retrieval.
 *
 * <p><strong>Responsibility.</strong> This service is the authority on guest
 * identity records. It enforces email uniqueness, hashes credentials before they
 * are ever persisted, and assigns the safe defaults that self-registration must
 * never let a caller override (loyalty tier and, critically, the non-admin role).
 *
 * <p><strong>Security posture.</strong> Passwords are stored only as
 * {@link PasswordEncoder} hashes, never in clear text, and self-registration always
 * mints {@code ROLE_USER}: a public caller can never provision an administrator.
 *
 * <p><strong>Thread safety.</strong> A stateless Spring singleton holding only its
 * injected repository and encoder; safe for concurrent request threads.
 */
@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guestRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registers a new guest account, hashing the password and applying safe defaults.
     *
     * <p>Rejects a duplicate email up front so the unique-email invariant is never
     * violated. The password is hashed before persistence, the loyalty tier defaults
     * to {@code STANDARD}, and the role is fixed to {@code ROLE_USER} regardless of
     * request content.
     *
     * <p>Read-write transactional: the insert must commit atomically. The
     * duplicate-email guard throws before any write, so the check-then-insert stays
     * consistent within the transaction.
     *
     * @param request the new guest's profile and credentials; {@code email} must be
     *                unique and {@code password} is stored only in hashed form.
     * @return a {@link GuestResponseDTO} view of the persisted guest, excluding
     *         sensitive fields such as the password hash.
     * @throws DuplicateResourceException if the email is already registered; thrown
     *         before any write, so nothing is persisted.
     */
    @Transactional
    public GuestResponseDTO registerGuest(GuestRequestDTO request) {

        if (guestRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already in use");
        }

        Guest guest = Guest.builder()
            .firstName(request.firstName())
            .lastName(request.lastName())
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .phone(request.phone())
            .nationality(request.nationality())
            .documentType(request.documentType())
            .documentNumber(request.documentNumber())
            .dateOfBirth(request.dateOfBirth())
            .loyaltyTier("STANDARD")
            .role("ROLE_USER") // self-registration never mints an admin
            .createdAt(java.time.LocalDateTime.now())
            .build();

        Guest savedGuest = guestRepository.save(guest);

        return new GuestResponseDTO(
            savedGuest.getId(),
            savedGuest.getFirstName(),
            savedGuest.getLastName(),
            savedGuest.getEmail(),
            savedGuest.getPhone(),
            savedGuest.getLoyaltyTier(),
            savedGuest.getCreatedAt()
        );
    }
    /**
     * Retrieves a guest's public profile by primary key.
     *
     * <p>Read-only transactional: a pure lookup that performs no writes, so the
     * transaction is marked {@code readOnly} to let the persistence provider skip
     * dirty-checking and flush overhead.
     *
     * @param id the guest's primary key; must not be {@code null}.
     * @return a {@link GuestResponseDTO} view of the guest.
     * @throws com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException if no
     *         guest exists with the given id.
     */
    @Transactional(readOnly = true)
    public GuestResponseDTO getGuestById(Long id) {
        Guest guest = guestRepository.findById(id)
            .orElseThrow(() -> new com.Abdelwahab.RoomBooking.exception.ResourceNotFoundException("Guest not found with ID: " + id));

        return new GuestResponseDTO(
            guest.getId(),
            guest.getFirstName(),
            guest.getLastName(),
            guest.getEmail(),
            guest.getPhone(),
            guest.getLoyaltyTier(),
            guest.getCreatedAt()
        );
    }
}