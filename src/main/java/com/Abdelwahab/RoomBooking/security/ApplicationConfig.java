package com.Abdelwahab.RoomBooking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.Abdelwahab.RoomBooking.repository.GuestRepository;

import lombok.RequiredArgsConstructor;

/**
 * Core authentication infrastructure for the Hotel Booking System.
 *
 * <p>Wires the beans that resolve identity and verify credentials: a
 * {@link UserDetailsService} that bridges Spring Security to {@code GuestRepository}
 * by email, a {@link DaoAuthenticationProvider} that couples that lookup with a
 * BCrypt {@link PasswordEncoder}, and the {@link AuthenticationManager} exposed for
 * programmatic login in the service layer.
 *
 * @see SecurityConfig
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final GuestRepository guestRepository;

    /**
     * Bridges Spring Security to the persistence layer.
     * Resolves the authenticated principal (Guest entity) by email to supply
     * credentials and database-driven authorities for authorization.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> guestRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /**
     * The primary authentication engine.
     * Couples the UserDetailsService (identity lookup) with the PasswordEncoder 
     * (credential verification) to validate login attempts.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Exposes Spring Security's auto-configured AuthenticationManager as a bean.
     * Calling config.getAuthenticationManager() retrieves the underlying instance 
     * that already auto-registers our custom AuthenticationProvider, avoiding manual
     * delegation wiring while enabling programmatic login in the service layer.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Enforces salted, adaptive hashing via BCrypt.
     * Deliberately CPU-intensive to mitigate brute-force and dictionary attacks
     * during password storage and verification.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}