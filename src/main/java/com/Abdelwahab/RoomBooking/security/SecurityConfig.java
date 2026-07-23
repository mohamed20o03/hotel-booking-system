package com.Abdelwahab.RoomBooking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;

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
 * <p><strong>Authentication vs authorization failures.</strong> A
 * {@link RestAuthenticationEntryPoint} is registered via
 * {@code exceptionHandling}, so an <em>unauthenticated</em> request to a protected
 * endpoint returns {@code 401 UNAUTHORIZED}. An <em>authenticated</em> caller who
 * lacks the required role is handled separately as {@code 403 FORBIDDEN} by
 * {@code GlobalExceptionHandler}. The two failure modes are therefore distinguishable
 * by clients.
 *
 * @see JwtAuthenticationFilter
 * @see RestAuthenticationEntryPoint
 * @see ApplicationConfig
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final RateLimitFilter rateLimitFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    /**
     * Builds the application's single {@link SecurityFilterChain}: disables CSRF,
     * declares the URL-based authorization rules, enforces a stateless session
     * policy, registers the {@link RestAuthenticationEntryPoint} (so unauthenticated
     * access yields {@code 401}), registers the {@link AuthenticationProvider}, and
     * inserts the {@link JwtAuthenticationFilter} ahead of
     * {@link UsernamePasswordAuthenticationFilter}.
     *
     * @param http the {@link HttpSecurity} builder supplied by Spring Security
     * @return the fully configured filter chain
     * @throws Exception if the chain cannot be assembled
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable()) // Disable CSRF since we are using JWTs
            .authorizeHttpRequests(auth -> auth
                // Public: registration, login, and logout only.
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                // Admin ban — requires ROLE_ADMIN (also guarded by @PreAuthorize).
                .requestMatchers("/api/auth/admin/**").hasRole("ADMIN")
                // Password change — requires any authenticated session.
                .requestMatchers("/api/auth/me/**").authenticated()
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
            // Unauthenticated access to a protected endpoint returns 401 (not the
            // default bare 403). Authenticated-but-unauthorized still yields 403 via
            // the access-denied path handled in GlobalExceptionHandler.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Prevents Spring Boot from also registering {@link RateLimitFilter} as a
     * stand-alone servlet filter. It is a {@code @Component}, so Boot would wire it
     * into the servlet container by default; combined with its explicit placement
     * in the security chain above, that would run it twice and double-count each
     * request. Disabling the auto-registration leaves the security-chain copy as the
     * only one.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
