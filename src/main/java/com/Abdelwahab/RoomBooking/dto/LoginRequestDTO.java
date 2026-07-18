package com.Abdelwahab.RoomBooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO carrying credentials for {@code POST /api/auth/login}, deserialized
 * from the JSON body. It is the authentication wire contract; on success the
 * endpoint issues a JWT rather than returning any entity, so no domain object is
 * exposed here.
 *
 * <p><strong>Validation intent.</strong> Both constraints are enforced by
 * {@code @Valid}; a violation yields {@code 400 Bad Request} via
 * {@code MethodArgumentNotValidException}. Note this is presence/format checking
 * only — whether the credentials are <em>correct</em> is an authentication
 * decision made downstream, not a bean-validation concern.
 *
 * @param email the account identity; {@code @NotBlank} and {@code @Email} so a
 *        malformed identifier is rejected before any credential lookup.
 * @param password the plaintext secret to verify; {@code @NotBlank} guarantees
 *        presence only.
 */
public record LoginRequestDTO(
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    String email,

    @NotBlank(message = "Password is required")
    String password
) {}
