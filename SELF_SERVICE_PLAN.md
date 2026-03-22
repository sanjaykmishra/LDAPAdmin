# Consolidated Plan: Self-Service Portal + Self-Registration

## Overview

Add two related capabilities to LDAPAdmin in a single cohesive feature:

1. **Self-Service Portal** — Authenticated end users manage their own LDAP entry (profile, password, group visibility)
2. **Self-Registration** — Unauthenticated visitors request a new LDAP account via an approval workflow

Both share a new `SELF_SERVICE` principal type, a unified `SelfServiceController`, a common frontend layout (`SelfServiceLayout.vue`), and extensions to the provisioning profile system. Self-registration piggybacks on the existing approval workflow.

> **Note:** The former Realm + UserTemplate model has been replaced by the unified Provisioning Profile concept (see `PROVISIONING_PROFILES_PLAN.md`). This plan reflects that architecture: `ProfileAttributeConfig` already includes `self_service_edit`, and `ProvisioningProfile` already includes `self_registration_allowed`.

---

## Phase 1: Database Migration (V26)

**File:** `src/main/resources/db/migration/V26__self_service.sql`

### 1a. Extend `directory_connections`

```sql
ALTER TABLE directory_connections
  ADD COLUMN self_service_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN self_service_login_attribute  VARCHAR(64) DEFAULT 'uid';
```

- `self_service_enabled` — per-directory toggle for the self-service portal
- `self_service_login_attribute` — attribute used to locate the user's DN during bind-as-self (e.g. `uid`, `sAMAccountName`)

### 1b. Create `registration_requests` table

```sql
CREATE TABLE registration_requests (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  directory_id          UUID NOT NULL REFERENCES directory_connections(id),
  profile_id            UUID NOT NULL REFERENCES provisioning_profiles(id),
  target_ou_dn          VARCHAR(500) NOT NULL,
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

### 1c. No migration needed for self-service attribute editing or self-registration flag

The provisioning profile migration (V25) already created:
- `profile_attribute_configs.self_service_edit` — marks which attributes end users can edit
- `provisioning_profiles.self_registration_allowed` — per-profile toggle for public registration

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
| GET | `/api/v1/self-service/template` | Profile attribute configs filtered to self-service-visible fields |
| GET | `/api/v1/self-service/profile` | Read own LDAP entry (all profile fields, editable flags indicated) |
| PUT | `/api/v1/self-service/profile` | Update own entry (only `selfServiceEdit` attributes accepted) |
| POST | `/api/v1/self-service/change-password` | Change own password (old + new required) |
| GET | `/api/v1/self-service/groups` | List own group memberships (read-only) |

**Public endpoints** (no auth — for self-registration):

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/self-service/register/directories` | List directories with self-registration enabled |
| GET | `/api/v1/self-service/register/profiles/{directoryId}` | List profiles with `selfRegistrationAllowed=true` |
| GET | `/api/v1/self-service/register/form/{profileId}` | Profile attribute config form schema (non-hidden, editable-on-create fields) |
| POST | `/api/v1/self-service/register/submit` | Submit registration request |
| POST | `/api/v1/self-service/register/verify/{token}` | Email verification callback |
| GET | `/api/v1/self-service/register/status/{requestId}` | Check request status (requires email for auth) |

### 3b. `SelfServiceService`

**File:** `src/main/java/com/ldapadmin/service/SelfServiceService.java`

**Profile operations:**
1. Load `DirectoryConnection` from JWT's `directoryId`
2. Resolve which `ProvisioningProfile` applies (from user's objectClasses → profile resolution via `ProvisioningProfileService.resolveProfileForDn()`)
3. Filter `ProfileAttributeConfig` entries: all fields visible, only `selfServiceEdit=true` fields writable
4. For reads: use service-account LDAP connection to fetch the user's entry by DN
5. For writes: validate only allowed attributes are present, then call `LdapUserService.updateUser()`
6. For password: bind-as-user with old password (proves knowledge), then `LdapUserService.resetPassword()` with new
7. For groups: `LdapGroupService` read-only membership lookup for the user's DN

**Registration operations:**
1. Validate form data against profile's `ProfileAttributeConfig` (required fields, input types, validation rules)
2. Apply computed expressions and default values via `ProvisioningProfileService.applyDefaults()`
3. Run attribute validation via `ProvisioningProfileService.validateAttributes()`
4. Create `RegistrationRequest` row with status `PENDING_VERIFICATION`
5. Send verification email with token (via existing email infrastructure)
6. On `verify/{token}`: mark `email_verified=true`, change status to `PENDING_APPROVAL`
7. Create `PendingApproval` with type `SELF_REGISTRATION` and payload referencing the registration request
8. Notify approvers via existing `ApprovalNotificationService`

### 3c. Extend approval workflow

**File:** `src/main/java/com/ldapadmin/entity/enums/ApprovalRequestType.java` — add `SELF_REGISTRATION`

**File:** `src/main/java/com/ldapadmin/service/ApprovalWorkflowService.java`

Add a branch in `approve()` for `SELF_REGISTRATION`:
1. Load the `RegistrationRequest` from the approval payload
2. Load the associated `ProvisioningProfile`
3. Build LDAP attributes with object classes, computed expressions, defaults, and fixed values from the profile
4. Construct DN from `rdnAttribute=value,targetOuDn`
5. Call `LdapUserService.createUser()` with the assembled entry
6. Add user to auto-join groups from `ProfileGroupAssignment`
7. If lifecycle policy exists on the profile, schedule expiration
8. Update `RegistrationRequest.status` to `APPROVED`
9. Send welcome/confirmation email to the registrant

For rejection: update status to `REJECTED`, send rejection email.

### 3d. New entity and repository

- `src/main/java/com/ldapadmin/entity/RegistrationRequest.java`
- `src/main/java/com/ldapadmin/entity/enums/RegistrationStatus.java`
- `src/main/java/com/ldapadmin/repository/RegistrationRequestRepository.java`

### 3e. Extend existing entities

**`DirectoryConnection.java`** — add `selfServiceEnabled` boolean + `selfServiceLoginAttribute` string

---

## Phase 4: Frontend — Shared Infrastructure

### 4a. Self-service API client

**File:** `frontend/src/api/selfservice.js`

All API calls for both self-service and registration endpoints.

### 4b. Self-service layout

**File:** `frontend/src/layouts/SelfServiceLayout.vue`

Simplified chrome: top bar with app name, user display name (when logged in), and logout. No sidebar, no admin navigation.

### 4c. Router additions

**File:** `frontend/src/router/index.js`

Public routes (no auth guard):
- `/self-service/login` → `SelfServiceLoginView.vue`
- `/register` → `RegisterView.vue`
- `/register/verify/:token` → `VerifyEmailView.vue`
- `/register/status/:requestId` → `RegistrationStatusView.vue`

Protected routes (require `SELF_SERVICE` principal):
- `/self-service/profile` → `SelfServiceProfileView.vue`
- `/self-service/password` → `SelfServicePasswordView.vue`
- `/self-service/groups` → `SelfServiceGroupsView.vue`

Router guard: check `principal.accountType` — redirect `SELF_SERVICE` users away from admin routes and vice versa.

### 4d. Auth store extension

**File:** `frontend/src/stores/auth.js`

Add `isSelfService` computed property. Extend `init()` / `/api/auth/me` to handle self-service tokens.

---

## Phase 5: Frontend — Views

### 5a. Self-service login

**File:** `frontend/src/views/selfservice/SelfServiceLoginView.vue`

Directory picker (dropdown of self-service-enabled directories), username + password. On success → `/self-service/profile`.

### 5b. Profile view

**File:** `frontend/src/views/selfservice/SelfServiceProfileView.vue`

Fetches profile attribute configs + user entry, renders with `FormField.vue`. Editable fields for `selfServiceEdit=true`, read-only for others. Computed expression fields shown as read-only with live preview.

### 5c. Password change

**File:** `frontend/src/views/selfservice/SelfServicePasswordView.vue`

Current password + new password + confirm. Calls `POST /api/v1/self-service/change-password`.

### 5d. Groups view

**File:** `frontend/src/views/selfservice/SelfServiceGroupsView.vue`

Read-only list of group memberships.

### 5e. Registration form

**File:** `frontend/src/views/selfservice/RegisterView.vue`

Directory selector → profile selector (filtered to `selfRegistrationAllowed=true`) → dynamic form from profile attribute configs → submit → "check your email" confirmation.

The form renders `ProfileAttributeConfig` fields filtered to those where `editableOnCreate=true`. Computed and fixed (HIDDEN_FIXED) attributes are applied server-side during provisioning. Validation rules (regex, min/max length, allowed values) are enforced client-side in real-time.

### 5f. Verification + status

**File:** `frontend/src/views/selfservice/VerifyEmailView.vue` — calls verify endpoint, shows result

**File:** `frontend/src/views/selfservice/RegistrationStatusView.vue` — displays request status

---

## Phase 6: Admin UI Extensions

### 6a. Profile attribute designer

The `SuperadminProfilesView.vue` attributes tab already includes the `selfServiceEdit` checkbox per attribute. No additional changes needed for the attribute designer.

### 6b. Directory settings

Add "Enable self-service portal" toggle and "Login attribute" field to the directory management UI.

### 6c. Profile settings

The `selfRegistrationAllowed` toggle already exists on the profile's General tab in `SuperadminProfilesView.vue`. No additional changes needed.

### 6d. Approvals view

Show `SELF_REGISTRATION` requests in `PendingApprovalsView.vue` with submitted attributes, profile name, and justification.

---

## Security

| Concern | Mitigation |
|---------|------------|
| Principal isolation | `SELF_SERVICE` tokens cannot access admin endpoints; enforced by Spring Security filter |
| DN pinning | DN always from JWT, never from request body |
| Rate limiting | On login + registration submit endpoints |
| CAPTCHA | reCAPTCHA v3 on registration submit |
| Spam prevention | Email verification required before request reaches approvers |
| LDAP injection | Validate/sanitize all attribute values before LDAP writes; profile validation rules enforced server-side |
| No public LDAP writes | Registration only writes to PostgreSQL; LDAP creation only after admin approval |
| Token expiry | Verification tokens expire after configurable TTL (default 24h) |
| Attribute whitelisting | Self-service writes only accept attributes where `selfServiceEdit=true` in profile config |

---

## File Summary

### New files (14)

| # | File | Purpose |
|---|------|---------|
| 1 | `V26__self_service.sql` | Migration (directory self-service fields + registration_requests table) |
| 2 | `RegistrationRequest.java` | Entity |
| 3 | `RegistrationStatus.java` | Enum |
| 4 | `RegistrationRequestRepository.java` | Repository |
| 5 | `SelfServiceController.java` | All endpoints |
| 6 | `SelfServiceService.java` | Business logic |
| 7 | `frontend/src/api/selfservice.js` | API client |
| 8 | `frontend/src/layouts/SelfServiceLayout.vue` | End-user layout |
| 9 | `SelfServiceLoginView.vue` | Login |
| 10 | `SelfServiceProfileView.vue` | Profile |
| 11 | `SelfServicePasswordView.vue` | Password change |
| 12 | `SelfServiceGroupsView.vue` | Group memberships |
| 13 | `RegisterView.vue` | Registration form |
| 14 | `VerifyEmailView.vue` | Email verify + status |

### Modified files (8)

| File | Change |
|------|--------|
| `PrincipalType.java` | Add `SELF_SERVICE` |
| `AuthPrincipal.java` | Add `dn`, `directoryId` fields |
| `JwtTokenService.java` | Add `dn`, `did` claims |
| `AuthController.java` | Add self-service login endpoint |
| `SecurityConfig.java` | Public + self-service route rules, principal isolation filter |
| `ApprovalRequestType.java` | Add `SELF_REGISTRATION` |
| `ApprovalWorkflowService.java` | Handle `SELF_REGISTRATION` approve/reject |
| `DirectoryConnection.java` | Add `selfServiceEnabled`, `selfServiceLoginAttribute` |

### Already in place (from provisioning profiles)

| Feature | Location |
|---------|----------|
| `selfServiceEdit` per attribute | `ProfileAttributeConfig.selfServiceEdit` |
| `selfRegistrationAllowed` per profile | `ProvisioningProfile.selfRegistrationAllowed` |
| Attribute validation (regex, length, allowed values) | `ProvisioningProfileService.validateAttributes()` |
| Computed expressions and defaults | `ProvisioningProfileService.applyDefaults()` |
| Auto-join groups | `ProfileGroupAssignment` |
| Lifecycle policies | `ProfileLifecyclePolicy` |
| Profile-scoped approval workflow | `ProfileApprovalConfig` + `ProfileApprover` |

---

## Implementation Order

1. **Phase 1** — Migration (directory self-service fields + registration_requests table)
2. **Phase 2** — Auth changes (principal, JWT, login endpoint, security config)
3. **Phase 3a-3b** — Self-service profile/password/groups (authenticated portal)
4. **Phase 3c-3d** — Registration entity + approval workflow extension
5. **Phase 4** — Frontend shared infrastructure (API client, layout, router, auth store)
6. **Phase 5a-5d** — Self-service portal views (login, profile, password, groups)
7. **Phase 5e-5f** — Registration views
8. **Phase 6** — Admin UI extensions (directory settings, approvals view)

The self-service portal (phases 2, 3a-3b, 5a-5d) can ship independently of self-registration (phases 3c-3d, 5e-5f), enabling incremental delivery.
