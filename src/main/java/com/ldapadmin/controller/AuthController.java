package com.ldapadmin.controller;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.AuthenticationService;
import com.ldapadmin.auth.LoginRateLimiter;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.auth.dto.LoginRequest;
import com.ldapadmin.auth.dto.LoginResponse;
import com.ldapadmin.config.AppProperties;
import com.ldapadmin.entity.AdminRealmRole;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.repository.AdminRealmRoleRepository;
import com.ldapadmin.repository.RealmRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication endpoints.
 *
 * <pre>
 *   POST /api/auth/login      — issue a JWT (set as httpOnly cookie; body carries principal info)
 *   POST /api/auth/logout     — clear the JWT cookie
 *   GET  /api/auth/me         — return current principal info
 *   GET  /api/auth/me/realms  — return realms the current principal is authorized for
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String JWT_COOKIE = "jwt";

    private final AuthenticationService   authenticationService;
    private final LoginRateLimiter        rateLimiter;
    private final AppProperties           appProperties;
    private final AdminRealmRoleRepository realmRoleRepo;
    private final RealmRepository          realmRepo;

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
     * Returns the username, account type, and id of the currently authenticated principal.
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
        return ResponseEntity.ok(body);
    }

    /**
     * Returns the realms that the current principal is authorized to access.
     * Superadmins see all realms; admins see only realms with an assigned realm role.
     */
    @GetMapping("/me/realms")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myRealms(
            @AuthenticationPrincipal AuthPrincipal principal) {

        if (principal == null) {
            throw new BadCredentialsException("Not authenticated");
        }

        List<Realm> realms;

        if (principal.type() == PrincipalType.SUPERADMIN) {
            realms = realmRepo.findAll();
        } else {
            realms = realmRoleRepo.findAllByAdminAccountId(principal.id()).stream()
                    .map(AdminRealmRole::getRealm)
                    .toList();
        }

        return realms.stream()
                .sorted(Comparator.comparing(Realm::getName, String.CASE_INSENSITIVE_ORDER))
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("name", r.getName());
                    m.put("directoryId", r.getDirectory().getId());
                    return m;
                })
                .toList();
    }
}
