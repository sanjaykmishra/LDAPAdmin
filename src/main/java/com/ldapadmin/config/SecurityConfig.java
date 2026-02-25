package com.ldapadmin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.auth.JwtAuthenticationFilter;
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
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // ── Superadmin-only management plane ──────────────────────────
                .requestMatchers("/api/superadmin/**").hasRole("SUPERADMIN")
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
}
