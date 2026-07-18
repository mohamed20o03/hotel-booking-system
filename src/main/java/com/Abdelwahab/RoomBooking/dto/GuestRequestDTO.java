package com.Abdelwahab.RoomBooking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for guest self-registration, deserialized from the JSON body of
 * {@code POST /api/auth/register}. It is the wire contract for account creation;
 * the {@code Guest} JPA entity is never bound directly, so persistence-only
 * concerns (hashed password, generated id, audit timestamps, loyalty tier) are
 * kept out of the public surface.
 *
 * <p><strong>Validation intent.</strong> Every constraint below is checked by
 * {@code @Valid} at the controller edge; any violation short-circuits to
 * {@code 400 Bad Request} via {@code MethodArgumentNotValidException} before the
 * service or database is touched.
 *
 * @param firstName the guest's given name; {@code @NotBlank} — a missing or
 *        whitespace-only value is rejected, as an account cannot be created
 *        anonymously.
 * @param lastName the guest's family name; {@code @NotBlank} for the same reason.
 * @param email the login identity and primary contact; {@code @NotBlank} and
 *        {@code @Email} — must be syntactically valid, since it doubles as the
 *        unique credential used at {@code /api/auth/login}.
 * @param password the plaintext secret supplied at sign-up; {@code @NotBlank}
 *        guarantees presence only. It is hashed server-side and never echoed back
 *        in any response DTO.
 * @param phone a reachable contact number; {@code @NotBlank} plus
 *        {@code @Pattern} enforcing an optional leading {@code +} followed by
 *        10–15 digits, rejecting formatted or extension-laden input at the edge.
 * @param nationality the guest's declared nationality; {@code @NotBlank}, a
 *        compliance/identity field required for booking.
 * @param documentType the kind of identity document (e.g. passport, national id);
 *        {@code @NotBlank}.
 * @param documentNumber the identity document's number; {@code @NotBlank}.
 * @param dateOfBirth the guest's birth date; {@code @NotNull} and {@code @Past} —
 *        a present or future date is nonsensical for a living guest and is
 *        rejected.
 */
public record GuestRequestDTO (

    @NotBlank(message = "First name is required")
    String firstName,

    @NotBlank(message = "Last name is required")
    String lastName,

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    String email,

    @NotBlank(message = "Password is required")
    String password,

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone format")
    String phone,

    @NotBlank(message = "Nationality is required")
    String nationality,

    @NotBlank(message = "Document type is required")
    String documentType,

    @NotBlank(message = "Document number is required")
    String documentNumber,

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    LocalDate dateOfBirth

) {}
