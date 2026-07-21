package com.Abdelwahab.RoomBooking.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Guest is the Spring Security principal. Its authorities are derived straight
 * from the persisted {@code role} column, so a promoted admin gains ROLE_ADMIN
 * on the next request with no token change — the JWT filter reloads the entity
 * every time. These tests pin that contract.
 *
 * <p>Also verifies the {@code banned} flag contract: a banned guest is treated as
 * a locked account by Spring Security, blocking new logins permanently.
 */
public class GuestTest {

    /**
     * Given a guest whose role column is ROLE_ADMIN;
     * when its Spring Security authorities are read; then they contain exactly
     * ROLE_ADMIN — authorities are derived straight from the persisted role.
     */
    @Test
    public void getAuthorities_reflectsRoleField() {
        Guest admin = Guest.builder().role("ROLE_ADMIN").build();

        assertThat(admin.getAuthorities())
            .extracting("authority")
            .containsExactly("ROLE_ADMIN");
    }

    /**
     * Given a guest built with no role set;
     * when its role and authorities are read; then the role defaults to ROLE_USER and the
     * authorities contain exactly ROLE_USER — the safe default for a new principal.
     */
    @Test
    public void role_defaultsToUser_whenNotSet() {
        Guest guest = Guest.builder().build();

        assertThat(guest.getRole()).isEqualTo("ROLE_USER");
        assertThat(guest.getAuthorities())
            .extracting("authority")
            .containsExactly("ROLE_USER");
    }

    // ── banned flag ───────────────────────────────────────────────

    /**
     * Given a freshly built guest with no banned flag set;
     * when {@code isAccountNonLocked()} is called; then it returns {@code true} —
     * the default state is unlocked so normal guests are not accidentally blocked.
     */
    @Test
    public void isAccountNonLocked_returnsTrueByDefault() {
        Guest guest = Guest.builder().build();

        assertThat(guest.isAccountNonLocked()).isTrue();
        assertThat(guest.isBanned()).isFalse();
    }

    /**
     * Given a guest whose {@code banned} field is {@code true};
     * when {@code isAccountNonLocked()} is called; then it returns {@code false} —
     * Spring Security's {@code DaoAuthenticationProvider} will throw
     * {@code LockedException} on the next login attempt, preventing new token issuance.
     */
    @Test
    public void isAccountNonLocked_returnsFalse_whenBanned() {
        Guest banned = Guest.builder().banned(true).build();

        assertThat(banned.isAccountNonLocked()).isFalse();
    }

    /**
     * Given a guest whose {@code banned} field is explicitly {@code false};
     * when {@code isAccountNonLocked()} is called; then it returns {@code true} —
     * an explicitly unlocked guest can authenticate normally.
     */
    @Test
    public void isAccountNonLocked_returnsTrue_whenNotBanned() {
        Guest guest = Guest.builder().banned(false).build();

        assertThat(guest.isAccountNonLocked()).isTrue();
    }
}
