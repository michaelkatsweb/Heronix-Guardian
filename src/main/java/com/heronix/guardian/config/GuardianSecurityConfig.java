package com.heronix.guardian.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Heronix Guardian.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class GuardianSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Development security configuration - permissive for testing.
     */
    @Bean
    @Profile({"dev", "test", "default"})
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
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/v1/guardian/webhooks/**") // Webhooks need CSRF disabled
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/health").permitAll()
                // Webhook endpoints (authenticated by signature)
                .requestMatchers("/api/v1/guardian/webhooks/**").permitAll()
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives("default-src 'self'; frame-ancestors 'none';"))
                .frameOptions(frame -> frame.deny())
            );

        return http.build();
    }
}
