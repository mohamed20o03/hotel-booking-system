package com.Abdelwahab.RoomBooking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * HTTP security configuration for the stateless, JWT-authenticated API.
 *
 * <p><strong>Session model.</strong> The chain is fully stateless: the session
 * creation policy is {@link SessionCreationPolicy#STATELESS}, so no server-side
 * session is ever created and every request must carry its own credentials. CSRF
 * protection is disabled because authentication rides on a {@code SameSite=Strict}
 * HttpOnly cookie combined with the stateless policy, which removes the cross-site
 * request-forgery vector that CSRF tokens defend against.
 *
 * <p><strong>Filter order.</strong> {@link JwtAuthenticationFilter} is registered
 * before {@link UsernamePasswordAuthenticationFilter} so that a valid JWT populates
 * the {@code SecurityContext} ahead of any form-login processing.
 *
 * <p><strong>Authorization rules.</strong>
 * <ul>
 *   <li><strong>Public</strong> — {@code /api/auth/**} (registration and login) and
 *       {@code GET /api/hotels/**} (browsing the catalogue).</li>
 *   <li><strong>Admin only</strong> — mutating catalogue verbs
 *       ({@code POST}/{@code PUT}/{@code DELETE} on {@code /api/hotels/**}),
 *       maintenance operations ({@code /api/maintenance/**}), and front-desk
 *       check-in ({@code PATCH /api/reservations/&#42;/check-in}).</li>
 *   <li><strong>Authenticated</strong> — every other request (booking, paying,
 *       my-reservations, cancel) requires a logged-in guest.</li>
 * </ul>
 *
 * <p>{@link EnableMethodSecurity} additionally activates {@code @PreAuthorize}
 * checks used at the method level in the controllers.
 *
 * @see JwtAuthenticationFilter
 * @see ApplicationConfig
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    /**
     * Builds the application's single {@link SecurityFilterChain}: disables CSRF,
     * declares the URL-based authorization rules, enforces a stateless session
     * policy, registers the {@link AuthenticationProvider}, and inserts the
     * {@link JwtAuthenticationFilter} ahead of
     * {@link UsernamePasswordAuthenticationFilter}.
     *
     * @param http the {@link HttpSecurity} builder supplied by Spring Security
     * @return the fully configured filter chain
     * @throws Exception if the chain cannot be assembled
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF since we are using JWTs
            .authorizeHttpRequests(auth -> auth
                // Public: registration/login and browsing the hotel catalogue (reads only).
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/hotels/**").permitAll()
                // Staff-only writes on the catalogue. These GET rules above already
                // allowed reads; these gate the mutating verbs to admins.
                .requestMatchers(HttpMethod.POST, "/api/hotels/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/hotels/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/hotels/**").hasRole("ADMIN")
                // Maintenance (block/unblock rooms) and front-desk check-in are admin-only.
                .requestMatchers("/api/maintenance/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/reservations/*/check-in").hasRole("ADMIN")
                // Everything else (booking, paying, my-reservations, cancel) needs a login.
                .anyRequest().authenticated()
            )
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
