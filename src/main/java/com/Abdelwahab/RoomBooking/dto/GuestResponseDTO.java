package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDateTime;

/**
 * Response DTO exposing a guest's public profile, serialized to the JSON body of
 * {@code GET /api/guests/{id}}. It is the outbound wire shape for a guest; the
 * {@code Guest} JPA entity is never serialized directly, which is what keeps
 * security-sensitive fields (notably the password hash) and internal relations
 * off the response.
 *
 * @param id the guest's stable identifier.
 * @param firstName the guest's given name.
 * @param lastName the guest's family name.
 * @param email the account/contact email.
 * @param phone the contact phone number.
 * @param loyaltyTier the guest's current loyalty standing.
 * @param createdAt when the account was registered.
 */
public record GuestResponseDTO (
    Long id,
    String firstName, 
    String lastName,
    String email, 
    String phone, 
    String loyaltyTier, 
    LocalDateTime createdAt
) {}
