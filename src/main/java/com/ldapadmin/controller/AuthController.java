package com.ldapadmin.controller;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.AuthenticationService;
import com.ldapadmin.auth.LoginRateLimiter;
import com.ldapadmin.auth.dto.LoginRequest;
import com.ldapadmin.auth.dto.LoginResponse;
import com.ldapadmin.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authentication endpoints.
 *
 * <pre>
 *   POST /api/auth/login   — issue a JWT (set as httpOnly cookie; body carries principal info)
 *   POST /api/auth/logout  — clear the JWT cookie
 *   GET  /api/auth/me      — return current principal info
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String JWT_COOKIE = "jwt";

    private final AuthenticationService authenticationService;
    private final LoginRateLimiter      rateLimiter;
    private final AppProperties         appProperties;

    /**
     * Authenticates the caller, sets an httpOnly JWT cookie, and returns
     * principal info (token is excluded from the response body via @JsonIgnore).
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest  request,
            HttpServletResponse response) {

        rateLimiter.check(request);

        LoginResponse resp = authenticationService.login(req);

        // Token goes into an httpOnly cookie — not accessible to JavaScript
        ResponseCookie cookie = ResponseCookie.from(JWT_COOKIE, resp.token())
                .httpOnly(true)
                .secure(appProperties.getCookie().isSecure())
                .sameSite("Strict")
                .path("/api/v1")
                .maxAge(Duration.ofMinutes(appProperties.getJwt().getExpiryMinutes()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(resp);
    }

    /**
     * Clears the JWT cookie, effectively logging the user out.
     * Permitted without authentication so expired sessions can also log out.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(JWT_COOKIE, "")
                .httpOnly(true)
                .secure(appProperties.getCookie().isSecure())
                .sameSite("Strict")
                .path("/api/v1")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the username, account type, id, and (for admins) tenant id
     * of the currently authenticated principal.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
            @AuthenticationPrincipal AuthPrincipal principal) {

        if (principal == null) {
            throw new BadCredentialsException("Not authenticated");
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("username",    principal.username());
        body.put("accountType", principal.type().name());
        body.put("id",          principal.id().toString());
        if (principal.tenantId() != null) {
            body.put("tenantId", principal.tenantId().toString());
        }
        return ResponseEntity.ok(body);
    }
}
