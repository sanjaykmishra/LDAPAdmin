# Consolidated Plan: Self-Service Portal + Self-Registration

## Overview

Add two related capabilities to LDAPAdmin in a single cohesive feature:

1. **Self-Service Portal** — Authenticated end users manage their own LDAP entry (profile, password, group visibility)
2. **Self-Registration** — Unauthenticated visitors request a new LDAP account via an approval workflow

Both share a new `SELF_SERVICE` principal type, a unified `SelfServiceController`, a common frontend layout (`SelfServiceLayout.vue`), and integrate with the **Provisioning Profile** system. Self-registration piggybacks on the existing approval workflow and uses profile-driven forms.

---

## Phase 1: Database Migration

**File:** Part of the provisioning profiles migration (see `PROVISIONING_PROFILES_PLAN.md`)

### 1a. Self-service fields on `ProfileAttributeConfig`

The `profile_attribute_configs` table already includes a `self_service_edit` boolean column that controls which attributes end users can edit (e.g. `mail`, `telephoneNumber`, `mobile`, `jpegPhoto`). The RDN, `cn`, and structural attributes stay `FALSE`. No additional schema changes needed for self-service editability.

### 1b. Extend `directory_connections`

```sql
ALTER TABLE directory_connections
  ADD COLUMN self_service_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN self_service_login_attribute  VARCHAR(64) DEFAULT 'uid';
```

- `self_service_enabled` — per-directory toggle for the self-service portal
- `self_service_login_attribute` — attribute used to locate the user's DN during bind-as-self (e.g. `uid`, `sAMAccountName`)

### 1c. Self-registration on provisioning profiles

Self-registration is controlled by the `self_registration_allowed` flag on `provisioning_profiles` (defined in the provisioning profiles migration). Only profiles with this flag set to `true` appear in the public registration form. No separate realm settings needed.

The `ProfileApprovalConfig` (from the provisioning profiles model) controls whether registration requests require approval, replacing the old realm-level approval settings.

Email verification settings are stored as application-level configuration:

- `selfRegistrationRequireEmailVerification` (boolean, application property)

### 1d. Create `registration_requests` table

```sql
CREATE TABLE registration_requests (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  directory_id          UUID NOT NULL REFERENCES directory_connections(id),
  profile_id            UUID NOT NULL REFERENCES provisioning_profiles(id),
  attributes            JSONB NOT NULL,
  email                 VARCHAR(255) NOT NULL,
  status                VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
  email_verified        BOOLEAN NOT NULL DEFAULT FALSE,
  verification_token    VARCHAR(255),
  verification_expires  TIMESTAMP WITH TIME ZONE,
  justification         TEXT,
  pending_approval_id   UUID REFERENCES pending_approvals(id),
  created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

Statuses: `PENDING_VERIFICATION`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `EXPIRED`.

Note: `target_ou_dn` is no longer stored here — it comes from the provisioning profile's `target_ou_dn` at provisioning time.

---

## Phase 2: Backend — Auth & Principal Changes

### 2a. Extend `PrincipalType`

**File:** `src/main/java/com/ldapadmin/auth/PrincipalType.java`

Add `SELF_SERVICE` — represents an end user authenticated via LDAP bind-as-self.

### 2b. Extend `AuthPrincipal`

**File:** `src/main/java/com/ldapadmin/auth/AuthPrincipal.java`

Add two optional fields for self-service sessions:

- `dn` (String) — the user's full LDAP DN (null for admin principals)
- `directoryId` (UUID) — which directory they authenticated against (null for admin principals)

Existing `id` field holds a synthetic UUID (e.g. UUID v5 from the DN) since self-service users have no `accounts` row.

### 2c. Extend `JwtTokenService`

**File:** `src/main/java/com/ldapadmin/auth/JwtTokenService.java`

Add claims for self-service tokens:
- `dn` — user's LDAP DN
- `did` — directory ID

The `parse()` method populates the new `AuthPrincipal` fields when `type == SELF_SERVICE`.

### 2d. Self-service login endpoint

**File:** `src/main/java/com/ldapadmin/controller/AuthController.java` (extend existing)

```
POST /api/v1/auth/self-service/login
Body: { username, password, directoryId }
```

Flow:
1. Look up `DirectoryConnection` by ID; verify `selfServiceEnabled == true`
2. Search for the user's DN: `(selfServiceLoginAttribute=username)` under baseDn using the service account
3. Attempt LDAP bind with the user's DN + supplied password
4. On success: issue JWT with `SELF_SERVICE` type, DN, and directoryId
5. Return JWT in httpOnly cookie (same as admin login)

### 2e. Security config

**File:** `src/main/java/com/ldapadmin/config/SecurityConfig.java`

```java
// Public (no auth)
.requestMatchers("/api/v1/auth/self-service/login").permitAll()
.requestMatchers("/api/v1/self-service/register/**").permitAll()

// Authenticated (any valid JWT)
.requestMatchers("/api/v1/self-service/profile/**").authenticated()
.requestMatchers("/api/v1/self-service/change-password").authenticated()
.requestMatchers("/api/v1/self-service/groups").authenticated()
.requestMatchers("/api/v1/self-service/template").authenticated()
```

Add a filter or `@PreAuthorize` to ensure `SELF_SERVICE` principals can **only** access `/api/v1/self-service/**` and not admin endpoints. Conversely, admin endpoints reject `SELF_SERVICE` tokens.

---

## Phase 3: Backend — Self-Service Controller & Service

### 3a. `SelfServiceController`

**File:** `src/main/java/com/ldapadmin/controller/SelfServiceController.java`

**Authenticated endpoints** (require `SELF_SERVICE` JWT):

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/self-service/template` | Provisioning profile's attribute configs filtered to self-service-visible fields |
| GET | `/api/v1/self-service/profile` | Read own LDAP entry (all profile attribute fields, editable flags indicated) |
| PUT | `/api/v1/self-service/profile` | Update own entry (only `self_service_edit=true` attributes accepted) |
| POST | `/api/v1/self-service/change-password` | Change own password (old + new required) |
| GET | `/api/v1/self-service/groups` | List own group memberships (read-only) |

**Public endpoints** (no auth — for self-registration):

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/self-service/register/directories` | List directories with self-service enabled |
| GET | `/api/v1/self-service/register/profiles/{directoryId}` | List provisioning profiles with `self_registration_allowed=true` |
| GET | `/api/v1/self-service/register/form/{profileId}` | Profile attribute config form schema (fields where `editable_on_create=true`, excluding hidden/fixed) |
| POST | `/api/v1/self-service/register/submit` | Submit registration request |
| POST | `/api/v1/self-service/register/verify/{token}` | Email verification callback |
| GET | `/api/v1/self-service/register/status/{requestId}` | Check request status (requires email for auth) |

### 3b. `SelfServiceService`

**File:** `src/main/java/com/ldapadmin/service/SelfServiceService.java`

**Profile operations:**
1. Load `DirectoryConnection` from JWT's `directoryId`
2. Resolve which `ProvisioningProfile` applies to the user (by matching their DN against profile `target_ou_dn` values, using `ProvisioningProfileService.resolveProfileForDn()`)
3. Filter `ProfileAttributeConfig` entries: all fields visible, only `self_service_edit=true` fields writable
4. For reads: use service-account LDAP connection to fetch the user's entry by DN
5. For writes: validate only allowed attributes are present, apply validation rules (regex, min/max length, allowed values from `ProfileAttributeConfig`), then call `LdapUserService.updateUser()`
6. For password: bind-as-user with old password (proves knowledge), then `LdapUserService.resetPassword()` with new
7. For groups: `LdapGroupService` read-only membership lookup for the user's DN

**Registration operations:**
1. Load the selected `ProvisioningProfile`; verify `self_registration_allowed=true`
2. Validate form data against the profile's `ProfileAttributeConfig` (required fields, input types, validation rules, allowed values)
3. Create `RegistrationRequest` row with status `PENDING_VERIFICATION`, referencing the `profile_id`
4. Send verification email with token (via existing email infrastructure)
5. On `verify/{token}`: mark `email_verified=true`, change status to `PENDING_APPROVAL`
6. Create `PendingApproval` with type `SELF_REGISTRATION` and payload referencing the registration request
7. Notify approvers via existing `ApprovalNotificationService` — approvers are determined by the profile's `ProfileApprovalConfig` and `ProfileApprover` list

### 3c. Extend approval workflow

**File:** `src/main/java/com/ldapadmin/entity/enums/ApprovalRequestType.java` — add `SELF_REGISTRATION`

**File:** `src/main/java/com/ldapadmin/service/ApprovalWorkflowService.java`

Add a branch in `approve()` for `SELF_REGISTRATION`:
1. Load the `RegistrationRequest` from the approval payload
2. Load the associated `ProvisioningProfile`
3. Call `ProvisioningProfileService.provisionUser()` with the stored attributes — this handles computed expressions, default values, fixed values, LDAP creation, and auto-join groups
4. Update `RegistrationRequest.status` to `APPROVED`
5. Send welcome/confirmation email to the registrant

For rejection: update status to `REJECTED`, send rejection email.

### 3d. New entity and repository

- `src/main/java/com/ldapadmin/entity/RegistrationRequest.java` — includes `profileId` (UUID) field referencing `provisioning_profiles`
- `src/main/java/com/ldapadmin/entity/enums/RegistrationStatus.java`
- `src/main/java/com/ldapadmin/repository/RegistrationRequestRepository.java`

### 3e. Extend existing entities

**`DirectoryConnection.java`** — add `selfServiceEnabled` boolean + `selfServiceLoginAttribute` string

---

## Phase 4: Frontend — Shared Infrastructure

### 4a. Self-service API client

**File:** `frontend/src/api/selfservice.js` *(new)*

All API calls for both self-service and registration endpoints. Registration endpoints use profile-based URLs (e.g., `register/profiles/{directoryId}` instead of `register/realms/{directoryId}`).

### 4b. Self-service layout — ALREADY EXISTS

**File:** `frontend/src/layouts/SelfServiceLayout.vue`

Simplified chrome: top bar with app name, user display name (when logged in), and logout. No sidebar, no admin navigation.

### 4c. Router — ALREADY CONFIGURED

**File:** `frontend/src/router/index.js`

All self-service and registration routes are already wired up:

Public routes (no auth guard):
- `/self-service/login` → `SelfServiceLoginView.vue`
- `/register` → `RegisterView.vue`
- `/register/verify/:token` → `VerifyEmailView.vue`
- `/register/status/:requestId` → `RegistrationStatusView.vue`

Protected routes (require `SELF_SERVICE` principal):
- `/self-service/profile` → `SelfServiceProfileView.vue`
- `/self-service/password` → `SelfServicePasswordView.vue`
- `/self-service/groups` → `SelfServiceGroupsView.vue`

**Remaining work:** add router guard to check `principal.accountType` — redirect `SELF_SERVICE` users away from admin routes and vice versa.

### 4d. Auth store extension

**File:** `frontend/src/stores/auth.js` *(modify)*

Add `isSelfService` computed property. Extend `init()` / `/api/auth/me` to handle self-service tokens.

---

## Phase 5: Frontend — Views (ALL ALREADY EXIST)

All seven self-service view files already exist in `frontend/src/views/selfservice/`. They will need to be wired up to the API client (Phase 4a) once the backend endpoints are implemented.

### 5a. Self-service login

**File:** `frontend/src/views/selfservice/SelfServiceLoginView.vue`

Directory picker (dropdown of self-service-enabled directories), username + password. On success → `/self-service/profile`.

### 5b. Profile view

**File:** `frontend/src/views/selfservice/SelfServiceProfileView.vue`

Fetches the user's resolved provisioning profile attribute configs + current LDAP entry, renders with `FormField.vue`. Editable fields for `self_service_edit=true`, read-only for others. Validation rules (regex, min/max length, allowed values) are enforced client-side.

### 5c. Password change

**File:** `frontend/src/views/selfservice/SelfServicePasswordView.vue`

Current password + new password + confirm. Calls `POST /api/v1/self-service/change-password`.

### 5d. Groups view

**File:** `frontend/src/views/selfservice/SelfServiceGroupsView.vue`

Read-only list of group memberships.

### 5e. Registration form

**File:** `frontend/src/views/selfservice/RegisterView.vue`

Directory selector → provisioning profile selector (filtered to `self_registration_allowed=true`) → dynamic form rendered from `ProfileAttributeConfig` (fields where `editable_on_create=true`) → submit → "check your email" confirmation.

Computed fields are applied server-side during provisioning. Fixed/hidden fields are not shown on the form.

### 5f. Verification + status

**File:** `frontend/src/views/selfservice/VerifyEmailView.vue` — calls verify endpoint, shows result

**File:** `frontend/src/views/selfservice/RegistrationStatusView.vue` — displays request status

---

## Phase 6: Admin UI Extensions

### 6a. Provisioning profile attribute designer

The `self_service_edit` column is already part of the profile attribute config table in the superadmin profile management page (`SuperadminProfilesView.vue`). No separate template designer changes needed.

### 6b. Directory settings

Add "Enable self-service portal" toggle and "Login attribute" field to the directory management UI.

### 6c. Profile settings

Self-registration is controlled by the `self_registration_allowed` flag on each provisioning profile, configured in the profile's General tab. Approval settings are configured in the profile's Approval tab. No separate realm settings needed.

### 6d. Approvals view

Show `SELF_REGISTRATION` requests in `PendingApprovalsView.vue` with submitted attributes, justification, and the provisioning profile that will be used.

---

## Security

| Concern | Mitigation |
|---------|------------|
| Principal isolation | `SELF_SERVICE` tokens cannot access admin endpoints; enforced by Spring Security filter |
| DN pinning | DN always from JWT, never from request body |
| Rate limiting | On login + registration submit endpoints |
| CAPTCHA | reCAPTCHA v3 on registration submit |
| Spam prevention | Email verification required before request reaches approvers |
| LDAP injection | Validate/sanitize all attribute values before LDAP writes |
| Attribute validation | Registration submissions validated against `ProfileAttributeConfig` rules (regex, allowed values, min/max length) |
| No public LDAP writes | Registration only writes to PostgreSQL; LDAP creation only after admin approval via `ProvisioningProfileService.provisionUser()` |
| Token expiry | Verification tokens expire after configurable TTL (default 24h) |
| Attribute whitelisting | Self-service writes only accept attributes where `selfServiceEdit=true` in profile config |

---

## File Summary

### New files (6)

| # | File | Purpose |
|---|------|---------|
| 1 | `RegistrationRequest.java` | Entity |
| 2 | `RegistrationStatus.java` | Enum |
| 3 | `RegistrationRequestRepository.java` | Repository |
| 4 | `SelfServiceController.java` | All endpoints |
| 5 | `SelfServiceService.java` | Business logic |
| 6 | `frontend/src/api/selfservice.js` | API client |

### Modified files (9)

| File | Change |
|------|--------|
| `PrincipalType.java` | Add `SELF_SERVICE` |
| `AuthPrincipal.java` | Add `dn`, `directoryId` fields |
| `JwtTokenService.java` | Add `dn`, `did` claims |
| `AuthController.java` | Add self-service login endpoint |
| `SecurityConfig.java` | Public + self-service route rules, principal isolation filter |
| `ApprovalRequestType.java` | Add `SELF_REGISTRATION` |
| `ApprovalWorkflowService.java` | Handle `SELF_REGISTRATION` approve/reject, use `ProvisioningProfileService.provisionUser()` |
| `DirectoryConnection.java` | Add `selfServiceEnabled`, `selfServiceLoginAttribute` |
| `frontend/src/stores/auth.js` | Add `isSelfService` computed, handle self-service tokens |

### Database migration

The self-service schema changes (`directory_connections` columns + `registration_requests` table) are included in the provisioning profiles migration since there are no existing deployments.

### Already in place (from provisioning profiles)

| Feature | Location |
|---------|----------|
| `selfServiceEdit` per attribute | `ProfileAttributeConfig.selfServiceEdit` |
| `selfRegistrationAllowed` per profile | `ProvisioningProfile.selfRegistrationAllowed` |
| `selfServiceEdit` admin UI checkbox | `SuperadminProfilesView.vue` attributes tab |
| `selfRegistrationAllowed` admin UI toggle | `SuperadminProfilesView.vue` general tab |
| Attribute validation (regex, length, allowed values) | `ProvisioningProfileService.validateAttributes()` |
| Computed expressions and defaults | `ProvisioningProfileService.applyDefaults()` |
| Auto-join groups | `ProfileGroupAssignment` |
| Lifecycle policies | `ProfileLifecyclePolicy` |
| Profile-scoped approval workflow | `ProfileApprovalConfig` + `ProfileApprover` |

### Already in place (frontend scaffolding)

| Feature | Location |
|---------|----------|
| Self-service layout | `frontend/src/layouts/SelfServiceLayout.vue` |
| Self-service login view | `frontend/src/views/selfservice/SelfServiceLoginView.vue` |
| Profile edit view | `frontend/src/views/selfservice/SelfServiceProfileView.vue` |
| Password change view | `frontend/src/views/selfservice/SelfServicePasswordView.vue` |
| Groups view | `frontend/src/views/selfservice/SelfServiceGroupsView.vue` |
| Registration form view | `frontend/src/views/selfservice/RegisterView.vue` |
| Email verification view | `frontend/src/views/selfservice/VerifyEmailView.vue` |
| Registration status view | `frontend/src/views/selfservice/RegistrationStatusView.vue` |
| All self-service routes | `frontend/src/router/index.js` (routes configured, guard TBD) |

---

## Implementation Order

1. **Phase 1** — Migration (self-service columns + registration_requests table, bundled with provisioning profiles migration)
2. **Phase 2** — Auth changes (principal, JWT, login endpoint, security config)
3. **Phase 3a-3b** — Self-service profile/password/groups (authenticated portal)
4. **Phase 3c-3d** — Registration entity + approval workflow extension
5. **Phase 4a, 4d** — Frontend API client + auth store extension (layout, routes, and views already exist)
6. **Phase 5** — Wire existing frontend views to API client; add router guard for principal isolation
7. **Phase 6** — Admin UI extensions (directory settings, approvals view)

The self-service portal (phases 2, 3a-3b, 5a-5d) can ship independently of self-registration (phases 3c-3d, 5e-5f), enabling incremental delivery.

### Dependencies on Provisioning Profiles

This plan depends on the provisioning profiles implementation (see `PROVISIONING_PROFILES_PLAN.md`):
- `provisioning_profiles` table and `ProvisioningProfile` entity must exist
- `ProfileAttributeConfig` with `self_service_edit` column must be implemented
- `ProfileApprovalConfig` and `ProfileApprover` entities must be available
- `ProvisioningProfileService.provisionUser()` and `resolveProfileForDn()` must be implemented
- The provisioning profiles frontend (profile management page) should be in place before adding self-service admin extensions
