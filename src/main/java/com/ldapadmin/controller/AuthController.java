package com.ldapadmin.controller;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.AuthenticationService;
import com.ldapadmin.auth.JwtTokenService;
import com.ldapadmin.auth.LoginRateLimiter;
import com.ldapadmin.auth.OidcAuthenticationService;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.auth.dto.LoginRequest;
import com.ldapadmin.auth.dto.LoginResponse;
import com.ldapadmin.config.AppProperties;
import com.ldapadmin.entity.AdminProfileRole;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.ProvisioningProfile;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapConnectionFactory;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.AdminProfileRoleRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.ProvisioningProfileRepository;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.nio.charset.StandardCharsets;
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
 *   POST /api/auth/login         — issue a JWT (set as httpOnly cookie; body carries principal info)
 *   POST /api/auth/logout        — clear the JWT cookie
 *   GET  /api/auth/me            — return current principal info
 *   GET  /api/auth/me/profiles   — return profiles the current principal is authorized for
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String JWT_COOKIE = "jwt";

    private final AuthenticationService          authenticationService;
    private final OidcAuthenticationService     oidcAuthenticationService;
    private final JwtTokenService               jwtTokenService;
    private final LoginRateLimiter              rateLimiter;
    private final AppProperties                 appProperties;
    private final AdminProfileRoleRepository    profileRoleRepo;
    private final ProvisioningProfileRepository profileRepo;
    private final DirectoryConnectionRepository dirRepo;
    private final LdapConnectionFactory         ldapConnectionFactory;
    private final LdapUserService               ldapUserService;
    private final com.ldapadmin.service.ApplicationSettingsService applicationSettingsService;

    /**
     * Public endpoint — returns whether the first-run setup wizard has been completed.
     */
    @GetMapping("/setup-status")
    public Map<String, Boolean> setupStatus() {
        return Map.of("setupCompleted", applicationSettingsService.getEntity().isSetupCompleted());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest  request,
            HttpServletResponse response) {

        rateLimiter.check(request);

        LoginResponse resp = authenticationService.login(req);

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
        if (principal.dn() != null) {
            body.put("dn", principal.dn());
        }
        if (principal.directoryId() != null) {
            body.put("directoryId", principal.directoryId().toString());
        }
        return ResponseEntity.ok(body);
    }

    // ── Self-service login ─────────────────────────────────────────────────

    public record SelfServiceLoginRequest(
            @NotNull UUID directoryId,
            @NotBlank String username,
            @NotBlank String password) {}

    @PostMapping("/self-service/login")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, String>> selfServiceLogin(
            @Valid @RequestBody SelfServiceLoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {

        rateLimiter.check(request);

        DirectoryConnection dc = dirRepo.findById(req.directoryId())
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", req.directoryId()));

        if (!dc.isSelfServiceEnabled()) {
            throw new BadCredentialsException("Self-service is not enabled for this directory");
        }

        // Search for the user's DN using the service account
        String loginAttr = dc.getSelfServiceLoginAttribute() != null
                ? dc.getSelfServiceLoginAttribute() : "uid";
        String filter = "(" + loginAttr + "=" + escapeLdapFilter(req.username()) + ")";
        List<LdapUser> users = ldapUserService.searchUsers(dc, filter, dc.getBaseDn(), 1, "dn");
        if (users.isEmpty()) {
            throw new BadCredentialsException("Invalid username or password");
        }
        String userDn = users.get(0).getDn();

        // Bind-as-user to verify password
        try (LDAPConnection conn = ldapConnectionFactory.openUnboundConnection(dc)) {
            BindResult result = conn.bind(new SimpleBindRequest(userDn, req.password()));
            if (result.getResultCode() != ResultCode.SUCCESS) {
                throw new BadCredentialsException("Invalid username or password");
            }
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid username or password");
        }

        // Issue self-service JWT
        UUID syntheticId = UUID.nameUUIDFromBytes(userDn.getBytes(StandardCharsets.UTF_8));
        AuthPrincipal principal = new AuthPrincipal(
                PrincipalType.SELF_SERVICE, syntheticId, req.username(), userDn, dc.getId());
        String token = jwtTokenService.issue(principal);

        ResponseCookie cookie = ResponseCookie.from(JWT_COOKIE, token)
                .httpOnly(true)
                .secure(appProperties.getCookie().isSecure())
                .sameSite("Strict")
                .path("/api/v1")
                .maxAge(Duration.ofMinutes(appProperties.getJwt().getExpiryMinutes()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", req.username());
        body.put("accountType", PrincipalType.SELF_SERVICE.name());
        body.put("id", syntheticId.toString());
        body.put("dn", userDn);
        body.put("directoryId", dc.getId().toString());
        return ResponseEntity.ok(body);
    }

    /** Escape special characters in an LDAP filter value per RFC 4515. */
    private static String escapeLdapFilter(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\5c");
                case '*'  -> sb.append("\\2a");
                case '('  -> sb.append("\\28");
                case ')'  -> sb.append("\\29");
                case '\0' -> sb.append("\\00");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── OIDC endpoints ─────────────────────────────────────────────────────

    @GetMapping("/oidc/authorize")
    public ResponseEntity<Map<String, String>> oidcAuthorize(HttpServletRequest request) {
        rateLimiter.check(request);

        String redirectUri = buildOidcRedirectUri(request);

        OidcAuthenticationService.AuthorizeResult result =
                oidcAuthenticationService.buildAuthorizationUrl(redirectUri);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("authorizationUrl", result.authorizationUrl());
        body.put("state", result.state());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/oidc/callback")
    public ResponseEntity<LoginResponse> oidcCallback(
            @RequestBody Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response) {

        rateLimiter.check(request);

        String code  = body.get("code");
        String state = body.get("state");

        if (code == null || state == null) {
            throw new BadCredentialsException("Missing code or state parameter");
        }

        OidcAuthenticationService.OidcLoginResult result =
                oidcAuthenticationService.handleCallback(code, state);

        ResponseCookie cookie = ResponseCookie.from(JWT_COOKIE, result.token())
                .httpOnly(true)
                .secure(appProperties.getCookie().isSecure())
                .sameSite("Strict")
                .path("/api/v1")
                .maxAge(Duration.ofMinutes(appProperties.getJwt().getExpiryMinutes()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        LoginResponse resp = new LoginResponse(result.token(), result.username(),
                result.accountType(), result.id());
        return ResponseEntity.ok(resp);
    }

    private String buildOidcRedirectUri(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();

        StringBuilder uri = new StringBuilder(scheme).append("://").append(host);
        if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
            uri.append(":").append(port);
        }
        uri.append("/oidc/callback");
        return uri.toString();
    }

    /**
     * Returns the profiles that the current principal is authorized to access.
     * Superadmins see all profiles; admins see only profiles with an assigned profile role.
     */
    @GetMapping("/me/profiles")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myProfiles(
            @AuthenticationPrincipal AuthPrincipal principal) {

        if (principal == null) {
            throw new BadCredentialsException("Not authenticated");
        }

        List<ProvisioningProfile> profiles;

        if (principal.type() == PrincipalType.SUPERADMIN) {
            profiles = profileRepo.findAll();
        } else {
            profiles = profileRoleRepo.findAllByAdminAccountId(principal.id()).stream()
                    .map(AdminProfileRole::getProfile)
                    .toList();
        }

        return profiles.stream()
                .sorted(Comparator.comparing(ProvisioningProfile::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("directoryId", p.getDirectory().getId());
                    return m;
                })
                .toList();
    }
}
