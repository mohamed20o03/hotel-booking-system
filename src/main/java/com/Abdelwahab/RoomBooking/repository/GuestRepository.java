package com.Abdelwahab.RoomBooking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Guest;

/**
 * Data-access layer for the {@link Guest} aggregate — the person a reservation is
 * booked for and the principal resolved from the security context.
 *
 * <p><strong>Aggregate role.</strong> The guest is the customer identity of the
 * domain. Both lookups below key off email, which the {@code guest} table enforces
 * as {@code unique}; that constraint is what lets email serve as the natural login
 * identifier and lets the two methods below assume at most one match.
 */
@Repository
public interface GuestRepository extends JpaRepository<Guest, Long> {

    /**
     * Resolves a guest by email address, the natural credential used to load the
     * authenticated principal during sign-in and to attribute bookings.
     *
     * <p>Relies on the {@code unique} constraint on {@code guest.email}: at most one
     * row can match, so an {@link Optional} rather than a collection is the correct
     * return shape.
     *
     * @param email the email address to resolve; matched exactly.
     * @return the matching guest, or {@link Optional#empty()} if no account uses that
     *         address.
     */
    Optional<Guest> findByEmail(String email);

    /**
     * Reports whether an account already uses the given email, without materialising
     * the entity. Intended as a pre-registration uniqueness check that fails fast
     * before an insert would trip the {@code unique} constraint on {@code guest.email}.
     *
     * @param email the email address to test; matched exactly.
     * @return {@code true} if a guest with that email exists, {@code false} otherwise.
     */
    boolean existsByEmail(String email);
}
