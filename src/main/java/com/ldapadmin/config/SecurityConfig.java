package com.ldapadmin.config;

import com.ldapadmin.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Production security configuration — stateless JWT bearer authentication.
 *
 * <p>Public endpoints:
 * <ul>
 *   <li>{@code POST /api/auth/login} — obtain a JWT</li>
 *   <li>{@code GET  /actuator/health} — health probe (no auth required)</li>
 * </ul>
 * All other requests require a valid {@code Authorization: Bearer <token>} header.
 * </p>
 *
 * <p>The session is stateless; no {@code HttpSession} is created by Spring Security.
 * CSRF is disabled because every state-changing call must carry the JWT.</p>
 *
 * <p>The {@link JwtAuthenticationFilter} runs before
 * {@link UsernamePasswordAuthenticationFilter}, populates
 * {@link org.springframework.security.core.context.SecurityContextHolder},
 * and lets the authorisation rules below decide whether to grant access.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // ── Public ────────────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // ── Superadmin-only management plane ──────────────────────────
                .requestMatchers("/api/superadmin/**").hasRole("SUPERADMIN")
                // ── Protected (any authenticated user) ────────────────────────
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                        res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")));

        return http.build();
    }
}
