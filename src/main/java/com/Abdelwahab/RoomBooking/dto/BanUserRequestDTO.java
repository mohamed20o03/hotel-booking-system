package com.Abdelwahab.RoomBooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for the admin-only ban endpoint ({@code POST /api/auth/admin/ban/{userId}}).
 *
 * <p>Carries the audit reason that will be stored in the Redis ban key
 * ({@code blacklist:user:<userId>}). The reason surfaces in security logs and lets
 * operators distinguish between different types of global revocations.
 */
public record BanUserRequestDTO(

        /**
         * The numeric ID of the guest to ban; must be a positive long.
         * Provided as a request body field for clarity (path variable carries the same
         * value but this keeps the DTO self-contained for service delegation).
         */
        @NotNull(message = "userId must not be null")
        @Positive(message = "userId must be a positive number")
        Long userId,

        /**
         * Machine-readable audit reason, e.g. {@code "ADMIN_BAN"}, {@code "SECURITY_BREACH"},
         * {@code "TERMS_VIOLATION"}. Stored verbatim in Redis; surfaced in filter logs when
         * the banned user's token is replayed.
         */
        @NotBlank(message = "reason must not be blank")
        String reason
) {}
