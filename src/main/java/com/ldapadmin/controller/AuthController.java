package com.ldapadmin.controller;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.AuthenticationService;
import com.ldapadmin.auth.dto.LoginRequest;
import com.ldapadmin.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Authentication endpoints.
 *
 * <pre>
 *   POST /api/auth/login   — issue a JWT (superadmin or tenant admin)
 *   GET  /api/auth/me      — return current principal info
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Authenticates the caller and returns a signed JWT.
     *
     * <p>Returns HTTP 401 on bad credentials via the global exception handler.</p>
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        LoginResponse resp = authenticationService.login(req);
        return ResponseEntity.ok(resp);
    }

    /**
     * Returns the username and type of the currently authenticated principal.
     * Useful for UI "who am I" checks.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
            @AuthenticationPrincipal AuthPrincipal principal) {

        if (principal == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        return ResponseEntity.ok(Map.of(
                "username",    principal.username(),
                "accountType", principal.type().name(),
                "id",          principal.id().toString()
        ));
    }
}
