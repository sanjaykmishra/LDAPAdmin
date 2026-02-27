package com.ldapadmin.auth;

import com.ldapadmin.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates signed JWT tokens using HMAC-SHA-256.
 *
 * <p>Token claims:
 * <ul>
 *   <li>{@code sub}  — username</li>
 *   <li>{@code type} — {@link PrincipalType} name</li>
 *   <li>{@code aid}  — account UUID</li>
 *   <li>{@code iat}, {@code exp} — standard issued-at / expiry</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String CLAIM_TYPE       = "type";
    private static final String CLAIM_ACCOUNT_ID = "aid";

    private final AppProperties appProperties;

    /**
     * Issues a signed JWT for the given principal.
     */
    public String issue(AuthPrincipal principal) {
        Instant now    = Instant.now();
        Instant expiry = now.plusSeconds(appProperties.getJwt().getExpiryMinutes() * 60L);

        return Jwts.builder()
                .subject(principal.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_TYPE, principal.type().name())
                .claim(CLAIM_ACCOUNT_ID, principal.id().toString())
                .signWith(signingKey())
                .compact();
    }

    /**
     * Parses and validates a JWT string, returning the embedded principal.
     *
     * @throws JwtException if the token is expired, malformed, or has an invalid signature
     */
    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        PrincipalType type = PrincipalType.valueOf(claims.get(CLAIM_TYPE, String.class));
        UUID          id   = UUID.fromString(claims.get(CLAIM_ACCOUNT_ID, String.class));

        return new AuthPrincipal(type, id, claims.getSubject());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private SecretKey signingKey() {
        byte[] keyBytes = Base64.getDecoder().decode(appProperties.getJwt().getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
