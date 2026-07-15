package com.Abdelwahab.RoomBooking.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Guest is the Spring Security principal. Its authorities are derived straight
 * from the persisted {@code role} column, so a promoted admin gains ROLE_ADMIN
 * on the next request with no token change — the JWT filter reloads the entity
 * every time. These tests pin that contract.
 */
public class GuestTest {

    @Test
    public void getAuthorities_reflectsRoleField() {
        Guest admin = Guest.builder().role("ROLE_ADMIN").build();

        assertThat(admin.getAuthorities())
            .extracting("authority")
            .containsExactly("ROLE_ADMIN");
    }

    @Test
    public void role_defaultsToUser_whenNotSet() {
        Guest guest = Guest.builder().build();

        assertThat(guest.getRole()).isEqualTo("ROLE_USER");
        assertThat(guest.getAuthorities())
            .extracting("authority")
            .containsExactly("ROLE_USER");
    }
}
