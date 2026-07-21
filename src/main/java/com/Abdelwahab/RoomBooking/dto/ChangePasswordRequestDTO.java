package com.Abdelwahab.RoomBooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for the guest self-service password-change endpoint
 * ({@code PATCH /api/auth/me/password}).
 *
 * <p>Requires both the current password (to re-authenticate the caller before mutating
 * credentials) and the desired new password. On success, all active tokens for the
 * caller are globally revoked so other devices are immediately forced to re-login.
 */
public record ChangePasswordRequestDTO(

        /**
         * The guest's current password in plaintext.
         * Verified against the stored BCrypt hash before any mutation takes place,
         * preventing a stolen session cookie from being used to silently change credentials.
         */
        @NotBlank(message = "currentPassword must not be blank")
        String currentPassword,

        /**
         * The desired new password in plaintext; must be at least 8 characters.
         * Will be BCrypt-hashed before persistence.
         */
        @NotBlank(message = "newPassword must not be blank")
        @Size(min = 8, message = "newPassword must be at least 8 characters")
        String newPassword
) {}
