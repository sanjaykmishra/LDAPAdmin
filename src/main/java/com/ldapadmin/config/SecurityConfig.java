package com.ldapadmin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.auth.JwtAuthenticationFilter;
import com.ldapadmin.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Production security configuration — stateless JWT authentication via
 * httpOnly cookie (with Authorization: Bearer header as a fallback for
 * API clients and tests).
 *
 * <p>Public endpoints:
 * <ul>
 *   <li>{@code POST /api/auth/login}  — obtain a JWT cookie</li>
 *   <li>{@code POST /api/auth/logout} — clear the JWT cookie</li>
 *   <li>{@code GET  /actuator/health} — health probe (no auth required)</li>
 * </ul>
 * All other requests require a valid JWT (cookie or Bearer header).
 * </p>
 *
 * <p>The session is stateless; no {@code HttpSession} is created by Spring Security.
 * CSRF is disabled because state-changing calls are authenticated via JWT, which
 * is not automatically attached by the browser in the same way cookies are for
 * cross-site requests (the cookie is SameSite=Strict).</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           ObjectMapper objectMapper) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // ── Security response headers ──────────────────────────────────────
            .headers(h -> h
                .frameOptions(fo -> fo.deny())
                .contentTypeOptions(cto -> {})
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // ── Public ────────────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // ── Superadmin-only management plane ──────────────────────────
                .requestMatchers("/api/v1/superadmin/**").hasRole("SUPERADMIN")
                // ── Protected (any authenticated user) ────────────────────────
                .anyRequest().authenticated()
            )
            // Return RFC 7807 ProblemDetail on missing/invalid JWT (not plain-text 401)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                            HttpStatus.UNAUTHORIZED, "Authentication required");
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    objectMapper.writeValue(res.getOutputStream(), pd);
                }));

        return http.build();
    }

    /**
     * CORS policy for the REST API.
     *
     * <p>In production the allowed origin should be set via the
     * {@code CORS_ALLOWED_ORIGIN} environment variable (e.g.
     * {@code https://ldapadmin.example.com}).  The default {@code *} is safe
     * for same-origin deployments where the frontend is served by the same
     * host, but must be tightened for split-origin setups.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow the configured frontend origin; falls back to same-origin ("*") if unset.
        String allowedOrigin = System.getenv("CORS_ALLOWED_ORIGIN");
        if (allowedOrigin != null && !allowedOrigin.isBlank()) {
            config.setAllowedOrigins(List.of(allowedOrigin));
            config.setAllowCredentials(true); // required for cookie-based auth
        } else {
            config.addAllowedOriginPattern("*");
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
