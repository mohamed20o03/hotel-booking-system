package com.Abdelwahab.RoomBooking.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

/**
 * A registered guest and the security principal for the application.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code guest} table. A guest is both a
 * customer of record (name, contact, travel-document, and loyalty details) and the
 * authenticated user who owns {@link Reservation}s. It implements Spring Security's
 * {@link UserDetails} so a persisted row can be loaded directly as the authentication
 * principal, with {@link #getUsername()} returning the {@link #email}.
 *
 * <p><strong>Relationships.</strong> The owning side of the guest/reservation association
 * lives on {@code Reservation.guest}; this class exposes no inverse collection.
 *
 * <p><strong>Security invariants.</strong>
 * <ul>
 *   <li>{@link #email} is <strong>unique</strong> and doubles as the login username.</li>
 *   <li>{@link #password} holds a BCrypt hash, never plaintext.</li>
 *   <li>{@link #role} is a single Spring Security authority carrying its {@code ROLE_}
 *       prefix. Self-registration always yields {@code ROLE_USER}; {@code ROLE_ADMIN}
 *       can only be seeded, never obtained through registration.</li>
 * </ul>
 */
@Entity
@Table(name = "guest")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Guest implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    /** Login username; <strong>unique</strong> across all guests. */
    @Column(nullable = false, length = 100, unique = true)
    private String email;

    /** BCrypt hash of the credential; never stored as plaintext. */
    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String phone;

    /** ISO 3166-1 alpha-2 country code (e.g. {@code EG}, {@code US}). */
    @Column(nullable = false, length = 2)
    private String nationality;

    /** Travel-document type presented at registration (e.g. {@code PASSPORT}, {@code NATIONAL_ID}). */
    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 100)
    private String documentNumber;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    /** Loyalty programme tier (e.g. {@code STANDARD}, {@code SILVER}, {@code GOLD}); defaults to {@code STANDARD}. */
    @Column(name = "loyalty_tier", length = 20)
    @Builder.Default
    private String loyaltyTier = "STANDARD";

    /**
     * Spring Security authority, stored with its {@code ROLE_} prefix
     * ({@code ROLE_USER} | {@code ROLE_ADMIN}) so it feeds {@link #getAuthorities()}
     * directly and {@code hasRole('ADMIN')} matches. Guests who self-register are always
     * {@code ROLE_USER}; an admin can only be seeded, never registered.
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "ROLE_USER";

    /**
     * When {@code true}, this account is administratively banned.
     *
     * <p>Spring Security checks {@link #isAccountNonLocked()} on every login attempt.
     * Setting this flag to {@code true} causes the {@link DaoAuthenticationProvider} to
     * throw {@link org.springframework.security.authentication.LockedException} before
     * a password is even checked, preventing the guest from obtaining a new token
     * regardless of whether their credentials are valid.
     *
     * <p>This complements the Redis-based token revocation in
     * {@link com.Abdelwahab.RoomBooking.security.TokenBlacklistService}: the Redis ban
     * invalidates all <em>existing</em> tokens immediately; this flag blocks <em>new</em>
     * logins permanently until an admin clears it.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean banned = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // A banned guest is treated as a locked account by Spring Security.
        // DaoAuthenticationProvider throws LockedException (→ 401) before any
        // password comparison, so a banned user cannot obtain a new token.
        return !banned;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
