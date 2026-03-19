# OIDC Authentication Implementation Plan

## Overview

Add OIDC (OpenID Connect) as a third authentication method alongside LOCAL and LDAP for admin/superadmin users. Uses Authorization Code Flow (server-side). The browser redirects to the IdP, the IdP redirects back with a code, the backend exchanges it for tokens, matches the user to an Account, and issues the existing JWT cookie.

## Architecture Decision: Auth Settings Scope

Auth provider configuration (OIDC issuer, LDAP server, etc.) stays at the **application level** in `ApplicationSettings` — these are per-deployment, not per-user. Every OIDC account authenticates against the same IdP.

### Current State: `adminAuthType` is Dead Code

The `admin_auth_type` column exists in the `application_settings` DB table and is mapped to `ApplicationSettings.adminAuthType`, but **nothing reads it**:

- `AuthenticationService.login()` decides LOCAL vs LDAP based on each `Account.authType`, not the global setting
- No controller or DTO exposes it
- The frontend Settings page (`SettingsView.vue`) has no authentication section at all — it only shows Branding, Session, SMTP, and S3

This means the column can be dropped without any migration of existing behavior.

### New Design: `enabledAuthTypes` Set

Replace the unused single-enum field with a **set of enabled methods**, allowing multiple auth types to coexist (e.g. LOCAL for break-glass superadmin + OIDC for regular admins):

```java
// Remove (unused):
private AccountType adminAuthType = AccountType.LOCAL;

// Add:
@ElementCollection
@CollectionTable(name = "enabled_auth_types", joinColumns = @JoinColumn(name = "settings_id"))
@Enumerated(EnumType.STRING)
@Column(name = "auth_type")
private Set<AccountType> enabledAuthTypes = Set.of(AccountType.LOCAL);
```

The login page uses this set to decide which UI elements to show (password form, SSO button, or both). Each `Account` retains its own `authType` field to record which method that specific user authenticates with.

| Setting | Level | Why |
|---------|-------|-----|
| OIDC issuer/clientId/secret | Application (`ApplicationSettings`) | Same IdP for all OIDC users |
| LDAP host/port/bindDn | Application (`ApplicationSettings`) | Same LDAP server for all LDAP users |
| Enabled auth methods | Application (`ApplicationSettings`) | Controls what the login page shows |
| Which method a user uses | Account (`Account.authType`) | Per-user, allows mixed methods |

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

### 2. Replace dead `adminAuthType` with `enabledAuthTypes`

**File:** `src/main/java/com/ldapadmin/entity/ApplicationSettings.java`

The existing `adminAuthType` field is unused (no service, controller, or frontend reads it). Remove it and add the new set:

```java
// Remove (dead code):
private AccountType adminAuthType = AccountType.LOCAL;

// Add:
@ElementCollection
@CollectionTable(name = "enabled_auth_types", joinColumns = @JoinColumn(name = "settings_id"))
@Enumerated(EnumType.STRING)
@Column(name = "auth_type")
private Set<AccountType> enabledAuthTypes = Set.of(AccountType.LOCAL);
```

Add an `enabledAuthTypes` gate in `AuthenticationService.login()` — reject login if the account's `authType` is not in the enabled set.

Expose `enabledAuthTypes` via the existing `/api/v1/settings/branding` response (already public/permitAll) so the login page can render the correct UI without an additional endpoint.

### 3. Add OIDC fields to `ApplicationSettings`

**File:** `src/main/java/com/ldapadmin/entity/ApplicationSettings.java`

```java
private String oidcIssuerUrl;          // e.g. https://accounts.google.com
private String oidcClientId;
private String oidcClientSecretEnc;    // AES-256-GCM encrypted via EncryptionService
private String oidcScopes;             // default: "openid profile email"
private String oidcUsernameClaim;      // claim to match against Account.username
                                       // default: "preferred_username" or "email"
```

### 4. DB migration

- Drop `admin_auth_type` column from `application_settings` (dead code, nothing reads it)
- Drop `chk_admin_auth_type` constraint
- Create `enabled_auth_types` join table with a default `LOCAL` row
- Add OIDC columns to `application_settings` table

### 5. Add dependency

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

### 6. Create `OidcAuthenticationService`

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

### 7. Add OIDC endpoints to `AuthController`

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

### 8. Update `SecurityConfig`

**File:** `src/main/java/com/ldapadmin/config/SecurityConfig.java`

Add OIDC endpoints to `permitAll` list:
```java
.requestMatchers(HttpMethod.GET, "/api/v1/auth/oidc/authorize").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/auth/oidc/callback").permitAll()
```

### 9. Update Settings DTOs

**Files:**
- `ApplicationSettingsRequest.java` — add OIDC config fields
- `ApplicationSettingsResponse.java` — add OIDC config fields (secret as boolean flag, not ciphertext)

### 10. Update `ApplicationSettingsService`

**File:** `src/main/java/com/ldapadmin/service/ApplicationSettingsService.java`

Handle OIDC client secret encryption/decryption same as existing LDAP bind password pattern.

### 11. Update Admin Management

**Files:**
- `AdminManagementService.java` — allow creating accounts with `authType=OIDC` (no password required)
- `AdminManagementController.java` / DTOs — expose OIDC as auth type option

Account linking: Superadmins create `Account` records with `authType=OIDC` and set the username to match the IdP claim. No self-registration — matches the existing LDAP admin pattern.

### 12. Frontend: Login Page

Add "Sign in with SSO" button (only visible when OIDC is configured):
1. Call `GET /api/v1/auth/oidc/authorize`
2. Redirect browser to returned URL
3. After IdP redirect back, callback page calls backend callback endpoint
4. On success, JWT cookie is set (same as current login), navigate to dashboard

Need a small callback page/route to handle the redirect from the IdP.

### 13. Frontend: Settings Page — New "Authentication" Section

The settings page (`SettingsView.vue`) currently has no authentication section at all. Add a new **Authentication** section (alongside Branding, Session, SMTP, S3) with two subsections:

**Enabled Auth Methods:**
- Multi-select checkboxes for LOCAL, LDAP, OIDC
- At least one method must remain enabled (validate client-side)

**LDAP Auth Provider** (shown when LDAP is enabled):
- Host, Port, SSL Mode, Trust All Certs, Trusted Cert PEM
- Service Account Bind DN, Bind Password
- User Search Base, Bind DN Pattern
- These fields already exist on `ApplicationSettings` but were never exposed in the UI

**OIDC Provider** (shown when OIDC is enabled):
- Issuer URL
- Client ID
- Client Secret (password field with "saved" indicator)
- Scopes (default: "openid profile email")
- Username Claim (default: "preferred_username")
- Test button (validate discovery doc is reachable)

### 14. Frontend: Admin Management

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
| Drop dead field, add enabled set | `ApplicationSettings.java` (remove `adminAuthType`, add `enabledAuthTypes`) |
| New entity fields | `ApplicationSettings.java` (OIDC provider config) |
| Auth type gate | `AuthenticationService.java` (check account's type is enabled) |
| New service | `OidcAuthenticationService.java` (new) |
| New endpoints | `AuthController.java` |
| Security config | `SecurityConfig.java` |
| Settings DTOs | `ApplicationSettingsRequest.java`, `ApplicationSettingsResponse.java` |
| Settings service | `ApplicationSettingsService.java` |
| Admin management | `AdminManagementService.java`, DTOs |
| DB migration | Drop dead `admin_auth_type` column, create `enabled_auth_types` table, add OIDC columns |
| Frontend login | Login view (SSO button + callback route, driven by `enabledAuthTypes`) |
| Frontend settings | Settings view (new Authentication section: enabled methods, LDAP provider, OIDC provider) |
| Frontend admin mgmt | Admin form (OIDC auth type option) |
| Dependency | `pom.xml` / `build.gradle` (Nimbus JOSE+JWT) |
