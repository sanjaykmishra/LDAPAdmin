# OIDC Authentication Implementation Plan

## Overview

Add OIDC (OpenID Connect) as a third authentication method alongside LOCAL and LDAP for admin/superadmin users. Uses Authorization Code Flow (server-side). The browser redirects to the IdP, the IdP redirects back with a code, the backend exchanges it for tokens, matches the user to an Account, and issues the existing JWT cookie.

## What Stays the Same

- JWT token format, claims, cookie handling
- `JwtAuthenticationFilter` (doesn't care how the JWT was originally issued)
- Permission model (`AdminRealmRole`, `AdminFeaturePermission`, `FeaturePermissionAspect`)
- Rate limiting (apply to OIDC callback too, by IP)

## Implementation Steps

### 1. Extend `AccountType` enum

**File:** `src/main/java/com/ldapadmin/entity/enums/AccountType.java`

Add `OIDC` value. Accounts with `authType=OIDC` have no `passwordHash` and no `ldapDn`. They're matched by the claim value from the ID token.

```java
public enum AccountType {
    LOCAL, LDAP, OIDC
}
```

### 2. Add OIDC fields to `ApplicationSettings`

**File:** `src/main/java/com/ldapadmin/entity/ApplicationSettings.java`

```java
private String oidcIssuerUrl;          // e.g. https://accounts.google.com
private String oidcClientId;
private String oidcClientSecretEnc;    // AES-256-GCM encrypted via EncryptionService
private String oidcScopes;             // default: "openid profile email"
private String oidcUsernameClaim;      // claim to match against Account.username
                                       // default: "preferred_username" or "email"
```

### 3. DB migration

- Add OIDC columns to `application_settings` table
- Add `OIDC` to `account_type` enum (if using DB-level enum constraint)

### 4. Add dependency

**File:** `pom.xml` or `build.gradle`

```xml
<!-- Nimbus JOSE+JWT for ID token validation -->
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.37</version>
</dependency>
```

Using Nimbus directly (rather than `spring-boot-starter-oauth2-client`) avoids pulling in session-based OAuth which conflicts with the stateless JWT design.

### 5. Create `OidcAuthenticationService`

**File:** `src/main/java/com/ldapadmin/auth/OidcAuthenticationService.java` (new)

Responsibilities:
- `buildAuthorizationUrl(state, nonce)` → URL string
  - Fetches IdP discovery doc from `{issuerUrl}/.well-known/openid-configuration`
  - Constructs authorize URL with `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state`, `nonce`, and PKCE `code_challenge`
- `exchangeCodeForTokens(code, redirectUri, codeVerifier)` → OidcTokenResponse
  - POST to IdP's token endpoint with the authorization code
- `validateIdToken(idToken, nonce)` → claims map
  - Fetch JWKS from IdP's `jwks_uri`
  - Verify RS256 signature, `iss`, `aud`, `exp`, `nonce`
- `resolveAccount(usernameClaim)` → Account
  - Lookup by username where `authType=OIDC` and `active=true`
  - Throw 401 if not found ("No account linked to this identity")

State/nonce storage: short-lived `ConcurrentHashMap<String, OidcState>` with TTL cleanup (or `@Cacheable` with expiry).

### 6. Add OIDC endpoints to `AuthController`

**File:** `src/main/java/com/ldapadmin/controller/AuthController.java`

#### `GET /api/v1/auth/oidc/authorize`

Initiates the OIDC flow:
1. Generate random `state`, `nonce`, and PKCE `code_verifier`/`code_challenge`
2. Store in server-side cache with short TTL (~5 min)
3. Return JSON with the IdP authorization URL
4. Frontend navigates browser to this URL

#### `POST /api/v1/auth/oidc/callback`

Completes the OIDC flow:
1. Receive `code` and `state` from IdP
2. Validate `state` matches stored value (CSRF protection)
3. Exchange code for tokens (server-to-server HTTPS)
4. Validate ID token (signature, iss, aud, exp, nonce)
5. Extract configured username claim
6. Lookup `Account` by username where `authType=OIDC` and `active=true`
7. If found → issue JWT cookie (same as existing login flow)
8. If not found → 401 "No account linked to this identity"

### 7. Update `SecurityConfig`

**File:** `src/main/java/com/ldapadmin/config/SecurityConfig.java`

Add OIDC endpoints to `permitAll` list:
```java
.requestMatchers(HttpMethod.GET, "/api/v1/auth/oidc/authorize").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/auth/oidc/callback").permitAll()
```

### 8. Update Settings DTOs

**Files:**
- `ApplicationSettingsRequest.java` — add OIDC config fields
- `ApplicationSettingsResponse.java` — add OIDC config fields (secret as boolean flag, not ciphertext)

### 9. Update `ApplicationSettingsService`

**File:** `src/main/java/com/ldapadmin/service/ApplicationSettingsService.java`

Handle OIDC client secret encryption/decryption same as existing LDAP bind password pattern.

### 10. Update Admin Management

**Files:**
- `AdminManagementService.java` — allow creating accounts with `authType=OIDC` (no password required)
- `AdminManagementController.java` / DTOs — expose OIDC as auth type option

Account linking: Superadmins create `Account` records with `authType=OIDC` and set the username to match the IdP claim. No self-registration — matches the existing LDAP admin pattern.

### 11. Frontend: Login Page

Add "Sign in with SSO" button (only visible when OIDC is configured):
1. Call `GET /api/v1/auth/oidc/authorize`
2. Redirect browser to returned URL
3. After IdP redirect back, callback page calls backend callback endpoint
4. On success, JWT cookie is set (same as current login), navigate to dashboard

Need a small callback page/route to handle the redirect from the IdP.

### 12. Frontend: Settings Page

Add OIDC configuration section alongside existing LDAP auth settings:
- Issuer URL
- Client ID
- Client Secret (password field with "saved" indicator)
- Scopes
- Username Claim
- Test button (validate discovery doc is reachable)

### 13. Frontend: Admin Management

Add `OIDC` option to auth type selector when creating/editing admin accounts.

## Security Considerations

| Concern | Mitigation |
|---------|------------|
| CSRF on OAuth flow | `state` parameter validated on callback |
| Token replay | `nonce` embedded in ID token and validated |
| Code interception | PKCE (`code_verifier`/`code_challenge`) |
| ID token forgery | Signature verified against IdP's JWKS |
| Client secret exposure | Encrypted via `EncryptionService` (AES-256-GCM) |
| Open redirect | `redirect_uri` hardcoded server-side, not from user input |
| Rate limiting | Apply `LoginRateLimiter` to OIDC callback by IP |

## File Change Summary

| Change | Files |
|--------|-------|
| New enum value | `AccountType.java` |
| New entity fields | `ApplicationSettings.java` |
| New service | `OidcAuthenticationService.java` (new) |
| New endpoints | `AuthController.java` |
| Security config | `SecurityConfig.java` |
| Settings DTOs | `ApplicationSettingsRequest.java`, `ApplicationSettingsResponse.java` |
| Settings service | `ApplicationSettingsService.java` |
| Admin management | `AdminManagementService.java`, DTOs |
| DB migration | New Flyway/Liquibase migration script |
| Frontend login | Login view (SSO button + callback route) |
| Frontend settings | Settings view (OIDC config section) |
| Frontend admin mgmt | Admin form (OIDC auth type option) |
| Dependency | `pom.xml` / `build.gradle` (Nimbus JOSE+JWT) |
