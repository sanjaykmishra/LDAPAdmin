package com.ldapadmin.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.ApplicationSettingsRepository;
import com.ldapadmin.service.EncryptionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the OIDC Authorization Code Flow with PKCE.
 *
 * <p>Uses the existing JJWT library (already on the classpath) for ID token
 * validation. Fetches JWKS and discovery documents using {@link HttpClient}.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OidcAuthenticationService {

    private final ApplicationSettingsRepository settingsRepo;
    private final AccountRepository             accountRepo;
    private final JwtTokenService               jwtTokenService;
    private final EncryptionService             encryptionService;
    private final ObjectMapper                  objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** In-memory store for pending OIDC flows, keyed by state parameter. */
    private final ConcurrentHashMap<String, OidcFlowState> pendingFlows = new ConcurrentHashMap<>();

    private static final long FLOW_TTL_MS = 5 * 60 * 1000L; // 5 minutes
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── Data classes ─────────────────────────────────────────────────────────

    record OidcFlowState(String nonce, String codeVerifier, String redirectUri, long createdAt) {}

    public record AuthorizeResult(String authorizationUrl, String state) {}

    public record OidcLoginResult(String token, String username, String accountType, String id) {}

    // ── Discovery document cache ─────────────────────────────────────────────

    private volatile Map<String, Object> discoveryCache;
    private volatile String discoveryIssuer;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Initiates the OIDC Authorization Code Flow.
     * Generates state, nonce, PKCE parameters and returns the IdP authorization URL.
     */
    public AuthorizeResult buildAuthorizationUrl(String redirectUri) {
        ApplicationSettings settings = requireSettings();
        validateOidcConfig(settings);

        Map<String, Object> discovery = fetchDiscovery(settings.getOidcIssuerUrl());
        String authEndpoint = (String) discovery.get("authorization_endpoint");
        if (authEndpoint == null) {
            throw new IllegalStateException("OIDC discovery missing authorization_endpoint");
        }

        String state = generateRandomString(32);
        String nonce = generateRandomString(32);
        String codeVerifier = generateRandomString(64);
        String codeChallenge = computeCodeChallenge(codeVerifier);

        // Store flow state for validation on callback
        cleanupExpiredFlows();
        pendingFlows.put(state, new OidcFlowState(nonce, codeVerifier, redirectUri, System.currentTimeMillis()));

        String scopes = settings.getOidcScopes() != null ? settings.getOidcScopes() : "openid profile email";

        String url = authEndpoint
                + "?response_type=code"
                + "&client_id=" + urlEncode(settings.getOidcClientId())
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&scope=" + urlEncode(scopes)
                + "&state=" + urlEncode(state)
                + "&nonce=" + urlEncode(nonce)
                + "&code_challenge=" + urlEncode(codeChallenge)
                + "&code_challenge_method=S256";

        return new AuthorizeResult(url, state);
    }

    /**
     * Completes the OIDC flow: validates state, exchanges code for tokens,
     * validates the ID token, and resolves the matching Account.
     */
    @Transactional
    public OidcLoginResult handleCallback(String code, String state) {
        // 1. Validate state
        OidcFlowState flow = pendingFlows.remove(state);
        if (flow == null || isExpired(flow)) {
            throw new BadCredentialsException("Invalid or expired OIDC state");
        }

        ApplicationSettings settings = requireSettings();
        validateOidcConfig(settings);

        Map<String, Object> discovery = fetchDiscovery(settings.getOidcIssuerUrl());

        // 2. Exchange code for tokens
        String tokenEndpoint = (String) discovery.get("token_endpoint");
        if (tokenEndpoint == null) {
            throw new IllegalStateException("OIDC discovery missing token_endpoint");
        }

        String clientSecret = settings.getOidcClientSecretEnc() != null
                ? encryptionService.decrypt(settings.getOidcClientSecretEnc())
                : null;

        StringBuilder tokenBody = new StringBuilder();
        tokenBody.append("grant_type=authorization_code");
        tokenBody.append("&code=").append(urlEncode(code));
        tokenBody.append("&redirect_uri=").append(urlEncode(flow.redirectUri()));
        tokenBody.append("&client_id=").append(urlEncode(settings.getOidcClientId()));
        if (clientSecret != null) {
            tokenBody.append("&client_secret=").append(urlEncode(clientSecret));
        }
        tokenBody.append("&code_verifier=").append(urlEncode(flow.codeVerifier()));

        Map<String, Object> tokenResponse = httpPostForm(tokenEndpoint, tokenBody.toString());

        if (!tokenResponse.containsKey("id_token")) {
            throw new BadCredentialsException("OIDC token exchange failed: no id_token in response");
        }

        String idTokenStr = (String) tokenResponse.get("id_token");

        // 3. Validate ID token
        Claims claims = validateIdToken(idTokenStr, settings, discovery, flow.nonce());

        // 4. Extract username claim and resolve account
        String usernameClaim = settings.getOidcUsernameClaim() != null
                ? settings.getOidcUsernameClaim() : "preferred_username";

        Object claimValue = claims.get(usernameClaim);
        if (claimValue == null) {
            throw new BadCredentialsException("ID token missing claim: " + usernameClaim);
        }

        String username = claimValue.toString();

        Account account = accountRepo.findByUsernameAndActiveTrue(username)
                .filter(a -> a.getAuthType() == AccountType.OIDC)
                .orElseThrow(() -> new BadCredentialsException(
                        "No active OIDC account linked to identity: " + username));

        account.setLastLoginAt(Instant.now());

        PrincipalType type = account.getRole() == AccountRole.SUPERADMIN
                ? PrincipalType.SUPERADMIN : PrincipalType.ADMIN;
        AuthPrincipal principal = new AuthPrincipal(type, account.getId(), account.getUsername());
        String token = jwtTokenService.issue(principal);

        return new OidcLoginResult(token, principal.username(), principal.type().name(),
                principal.id().toString());
    }

    // ── ID token validation using JJWT ───────────────────────────────────────

    private Claims validateIdToken(String idTokenStr, ApplicationSettings settings,
                                    Map<String, Object> discovery, String expectedNonce) {
        String jwksUri = (String) discovery.get("jwks_uri");
        if (jwksUri == null) {
            throw new IllegalStateException("OIDC discovery missing jwks_uri");
        }

        // Fetch JWKS and build signing key
        Map<String, Object> jwks = httpGetJson(jwksUri);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        if (keys == null || keys.isEmpty()) {
            throw new BadCredentialsException("OIDC JWKS contains no keys");
        }

        // Decode the JWT header to find the key id (kid)
        String[] parts = idTokenStr.split("\\.");
        if (parts.length != 3) {
            throw new BadCredentialsException("Malformed ID token");
        }
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        Map<String, Object> header;
        try {
            header = objectMapper.readValue(headerJson, new TypeReference<>() {});
        } catch (IOException e) {
            throw new BadCredentialsException("Malformed ID token header");
        }

        String kid = (String) header.get("kid");
        String alg = (String) header.get("alg");

        // Find matching key
        Map<String, Object> matchingKey = null;
        for (Map<String, Object> key : keys) {
            if (kid != null && kid.equals(key.get("kid"))) {
                matchingKey = key;
                break;
            }
        }
        if (matchingKey == null) {
            // Fall back to first RSA key
            for (Map<String, Object> key : keys) {
                if ("RSA".equals(key.get("kty"))) {
                    matchingKey = key;
                    break;
                }
            }
        }
        if (matchingKey == null) {
            throw new BadCredentialsException("No matching key found in JWKS");
        }

        PublicKey publicKey = buildRsaPublicKey(matchingKey);

        // Parse and verify the ID token using JJWT
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(settings.getOidcIssuerUrl())
                    .requireAudience(settings.getOidcClientId())
                    .build()
                    .parseSignedClaims(idTokenStr)
                    .getPayload();
        } catch (Exception e) {
            log.warn("OIDC ID token validation failed: {}", e.getMessage());
            throw new BadCredentialsException("Invalid ID token: " + e.getMessage());
        }

        // Verify nonce
        String tokenNonce = claims.get("nonce", String.class);
        if (!expectedNonce.equals(tokenNonce)) {
            throw new BadCredentialsException("ID token nonce mismatch");
        }

        return claims;
    }

    private PublicKey buildRsaPublicKey(Map<String, Object> jwk) {
        try {
            String n = (String) jwk.get("n");
            String e = (String) jwk.get("e");
            if (n == null || e == null) {
                throw new BadCredentialsException("JWK missing 'n' or 'e' parameter");
            }

            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));

            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePublic(spec);
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadCredentialsException("Failed to construct RSA public key from JWK", ex);
        }
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> httpGetJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP GET " + url + " returned " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), Map.class);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch " + url + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> httpPostForm(String url, String formBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OIDC token exchange failed: HTTP {} — {}", response.statusCode(), response.body());
                throw new BadCredentialsException("OIDC token exchange failed: HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), Map.class);
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("OIDC token exchange request failed: " + e.getMessage(), e);
        }
    }

    // ── Discovery document ───────────────────────────────────────────────────

    private Map<String, Object> fetchDiscovery(String issuerUrl) {
        if (discoveryCache != null && issuerUrl.equals(discoveryIssuer)) {
            return discoveryCache;
        }

        String discoveryUrl = issuerUrl.endsWith("/")
                ? issuerUrl + ".well-known/openid-configuration"
                : issuerUrl + "/.well-known/openid-configuration";

        Map<String, Object> doc = httpGetJson(discoveryUrl);
        discoveryCache = doc;
        discoveryIssuer = issuerUrl;
        return doc;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ApplicationSettings requireSettings() {
        return settingsRepo.findFirstBy()
                .orElseThrow(() -> new BadCredentialsException("Application settings not configured"));
    }

    private void validateOidcConfig(ApplicationSettings settings) {
        if (settings.getOidcIssuerUrl() == null || settings.getOidcIssuerUrl().isBlank()) {
            throw new IllegalStateException("OIDC issuer URL not configured");
        }
        if (settings.getOidcClientId() == null || settings.getOidcClientId().isBlank()) {
            throw new IllegalStateException("OIDC client ID not configured");
        }
    }

    private String computeCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String generateRandomString(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isExpired(OidcFlowState flow) {
        return System.currentTimeMillis() - flow.createdAt() > FLOW_TTL_MS;
    }

    private void cleanupExpiredFlows() {
        long now = System.currentTimeMillis();
        pendingFlows.entrySet().removeIf(e -> now - e.getValue().createdAt() > FLOW_TTL_MS);
    }
}
