package com.heronix.guardian.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import com.heronix.guardian.security.DeviceVerificationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Security configuration for Heronix Guardian.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class GuardianSecurityConfig {

    private final DeviceVerificationFilter deviceVerificationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Default/development security configuration - permissive for testing.
     * Active when no specific profile (like "prod") is active.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Allow H2 console in dev
                .requestMatchers("/h2-console/**").permitAll()
                // Allow Swagger/OpenAPI
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                // Allow actuator health check
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Allow all API endpoints in dev mode
                .requestMatchers("/api/**").permitAll()
                .anyRequest().permitAll()
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable()) // For H2 console
            );

        return http.build();
    }

    /**
     * Production security configuration - strict.
     */
    @Bean
    @Profile("prod")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/v1/guardian/webhooks/**") // Webhooks need CSRF disabled
                .ignoringRequestMatchers("/api/v1/parent-portal/**") // Parent portal uses device auth
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/health").permitAll()
                // Parent Portal gateway (device auth handled by DeviceVerificationFilter)
                .requestMatchers("/api/v1/parent-portal/**").permitAll()
                // Webhook endpoints (authenticated by signature)
                .requestMatchers("/api/v1/guardian/webhooks/**").permitAll()
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )
            .addFilterBefore(deviceVerificationFilter, AuthorizationFilter.class)
            .headers(headers -> headers
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives("default-src 'self'; frame-ancestors 'none';"))
                .frameOptions(frame -> frame.deny())
            );

        return http.build();
    }
}
