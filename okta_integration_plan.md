# Okta Integration Analysis for LDAPAdmin

## Executive Summary

Okta integration spans two distinct surface areas: **authentication** (Okta as an SSO provider for LDAPAdmin admin/operator login) and **provisioning** (Okta as a SCIM client pushing user lifecycle events into LDAPAdmin-managed LDAP directories). The authentication side is nearly free — the existing OIDC flow works with Okta today. The provisioning side is the high-value, high-effort work.

---

## Business Value

### 1. Authentication (SSO) — Moderate value, near-zero effort

LDAPAdmin already supports OIDC login (`AccountType.OIDC`). Okta is an OIDC-compliant IdP, so admin SSO via Okta works today with configuration alone (issuer URL, client ID, client secret). The value is table-stakes: enterprises with Okta expect SSO for every internal tool.

**Gap**: The current OIDC flow only supports a single IdP configuration (stored in `ApplicationSettings`). Enterprises with multiple IdPs (Okta for corporate, Azure AD for contractors) would need multi-provider support — a future enhancement, not a blocker.

### 2. Provisioning (SCIM) — High value, significant effort

This is where the real value lies. Enterprises use Okta as their identity governance hub. When Okta assigns a user to an application, it expects to push that user into the downstream system via SCIM. When the user is deactivated in Okta, it expects to push a deactivation.

**Value drivers:**

| Use Case | Impact |
|----------|--------|
| **Automated account creation** | Okta assigns user to "LDAPAdmin" app → SCIM creates LDAP entry via provisioning profile. Eliminates manual user creation. |
| **Automated deprovisioning** | User removed from Okta app assignment → SCIM disables/deletes LDAP entry. Closes the #1 compliance gap: orphaned accounts. |
| **Attribute sync** | Okta profile changes (title, department, manager) flow into LDAP attributes automatically. Keeps directory current without HR sync lag. |
| **Group push** | Okta group assignments sync to LDAP group memberships. Enables centralized access management. |
| **Compliance evidence** | SCIM provisioning events create audit trail entries, feeding SOX/SOC 2 access review evidence automatically. |
| **Competitive necessity** | Okta's app catalog lists 7,000+ SCIM-integrated apps. Absence from this catalog is a disqualifier for Okta-centric enterprises. |

### 3. HR-Enriched Lifecycle — Compounding value

Okta integrates with Workday, BambooHR, and other HRIS systems. When combined with LDAPAdmin's existing HR sync:

- Okta handles **authentication and application assignment** (who can access what)
- LDAPAdmin handles **LDAP directory operations** (the actual provisioning target)
- HR data from both Okta's profile and LDAPAdmin's HR sync **enriches access reviews**

This three-way integration (HR → Okta → LDAPAdmin → LDAP) is the standard enterprise identity architecture.

---

## Technical Approach

### Part 1: Authentication (OIDC SSO) — Already Supported

The existing architecture handles this:

- `AccountType.OIDC` in the `Account` entity
- OIDC settings in `ApplicationSettings`: `oidcIssuerUrl`, `oidcClientId`, `oidcClientSecret`, `oidcScopes`, `oidcUsernameClaim`
- Auth endpoints: `GET /api/v1/auth/oidc/authorize`, `POST /api/v1/auth/oidc/callback`
- Frontend login redirects to Okta's authorization endpoint, callback exchanges code for tokens

**Only documentation needed** — a setup guide for creating an Okta OIDC application and configuring the redirect URI.

**Optional enhancement**: Support Okta-specific claims for role mapping (e.g., map Okta group `LDAPAdmin-Superadmins` → `AccountRole.SUPERADMIN`). Currently, OIDC-authenticated users must have a pre-created `Account` record with role assigned manually.

### Part 2: SCIM Provisioning Server — New Capability

LDAPAdmin needs to act as a **SCIM 2.0 server** (RFC 7643/7644) that Okta pushes to. This is the inverse of the HR sync pattern — instead of LDAPAdmin pulling from an external system, Okta pushes into LDAPAdmin.

#### SCIM 2.0 Protocol Requirements

Okta's SCIM integration requires these endpoints:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/scim/v2/Users` | GET | List/search users (Okta uses `filter=userName eq "..."`) |
| `/scim/v2/Users` | POST | Create user |
| `/scim/v2/Users/{id}` | GET | Get user by ID |
| `/scim/v2/Users/{id}` | PUT | Replace user |
| `/scim/v2/Users/{id}` | PATCH | Partial update (Okta uses this for activate/deactivate) |
| `/scim/v2/Users/{id}` | DELETE | Delete user |
| `/scim/v2/Groups` | GET | List/search groups |
| `/scim/v2/Groups` | POST | Create group |
| `/scim/v2/Groups/{id}` | PATCH | Update group membership |
| `/scim/v2/Groups/{id}` | DELETE | Delete group |
| `/scim/v2/ServiceProviderConfig` | GET | Capability discovery |
| `/scim/v2/Schemas` | GET | Schema discovery |
| `/scim/v2/ResourceTypes` | GET | Resource type discovery |

#### Architecture

```
┌──────────┐    SCIM/HTTPS     ┌─────────────────────────────────────────────┐
│          │ ────────────────→  │  ScimController                             │
│   Okta   │  Bearer token     │    ↓                                        │
│          │ ←────────────────  │  ScimProvisioningService                    │
└──────────┘   SCIM responses  │    ↓                                        │
                               │  ProvisioningProfileService (attr mapping)  │
                               │    ↓                                        │
                               │  LdapOperationService (LDAP writes)         │
                               │    ↓                                        │
                               │  AuditService (compliance trail)            │
                               └─────────────────────────────────────────────┘
```

#### New Components

**1. SCIM Controller** (`ScimController.java`)

REST controller implementing SCIM 2.0 endpoints. Must handle:
- SCIM JSON media type (`application/scim+json`)
- SCIM error response format (`urn:ietf:params:scim:api:messages:2.0:Error`)
- SCIM list response pagination (`startIndex`, `count`, `totalResults`, `itemsPerPage`)
- SCIM filter parsing (Okta primarily uses `userName eq "value"`)
- SCIM PATCH operations (`op: "replace"`, `path: "active"`, `value: false`)

**2. SCIM Provisioning Service** (`ScimProvisioningService.java`)

Maps between SCIM `User`/`Group` resources and LDAPAdmin's domain model:

```java
// SCIM User → LDAP entry creation
public ScimUser createUser(UUID directoryId, UUID profileId, ScimUser scimUser) {
    // 1. Map SCIM attributes → profile attribute configs
    // 2. Validate against profile rules
    // 3. Call LdapOperationService.createUser()
    // 4. Store SCIM ID ↔ LDAP DN mapping
    // 5. Audit as SCIM_USER_PROVISIONED
    // 6. Return SCIM User with id, meta, etc.
}

// SCIM PATCH active=false → LDAP disable
public ScimUser deactivateUser(UUID directoryId, String scimId) {
    // 1. Resolve SCIM ID → LDAP DN
    // 2. Call LdapOperationService.disableUser()
    // 3. Optionally trigger offboard playbook
    // 4. Audit as SCIM_USER_DEACTIVATED
}
```

**3. SCIM Entity Mapping Table** (`scim_resource_mappings`)

Maps SCIM resource IDs to LDAP DNs (since LDAP DNs can change via moves/renames):

```sql
CREATE TABLE scim_resource_mappings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id    UUID NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    profile_id      UUID REFERENCES provisioning_profiles(id) ON DELETE SET NULL,
    scim_id         VARCHAR(255) NOT NULL,        -- Okta's external ID
    resource_type   VARCHAR(10) NOT NULL,          -- 'User' or 'Group'
    ldap_dn         VARCHAR(2048) NOT NULL,        -- Current LDAP DN
    okta_user_name  VARCHAR(255),                  -- For userName filter lookups
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (directory_id, scim_id, resource_type)
);
CREATE INDEX idx_scim_username ON scim_resource_mappings(directory_id, okta_user_name) WHERE resource_type = 'User';
```

**4. SCIM Configuration Entity** (`ScimProviderConfig`)

Per-directory SCIM endpoint configuration:

| Field | Purpose |
|-------|---------|
| `directoryId` | Target LDAP directory |
| `profileId` | Provisioning profile for attribute mapping and target OU |
| `enabled` | Master toggle |
| `bearerToken` | Encrypted API token for Okta to authenticate |
| `userNameAttribute` | LDAP attribute that maps to SCIM `userName` (default: `uid`) |
| `createDisabledUsers` | Whether to create users in disabled state initially |
| `deleteAction` | What to do on SCIM DELETE: `DISABLE`, `DELETE`, or `MOVE` |
| `groupPushEnabled` | Whether to accept SCIM group operations |
| `deprovisionAction` | What to do on `active=false`: run offboard playbook, disable, or delete |

**5. SCIM DTOs** (RFC 7643 compliant)

```java
public record ScimUser(
    String id,                          // LDAPAdmin-generated UUID
    String externalId,                  // Okta's ID for the user
    ScimMeta meta,
    String userName,
    ScimName name,                      // { givenName, familyName }
    List<ScimEmail> emails,
    String displayName,
    String title,
    String department,
    boolean active,
    List<ScimGroup> groups              // Read-only, derived from LDAP
) {}

public record ScimListResponse<T>(
    List<String> schemas,               // ["urn:ietf:params:scim:api:messages:2.0:ListResponse"]
    int totalResults,
    int startIndex,
    int itemsPerPage,
    List<T> Resources
) {}
```

**6. Authentication**

Okta authenticates to SCIM endpoints using a Bearer token. This must bypass JWT auth:

```java
// SecurityConfig.java addition
.requestMatchers("/scim/v2/**").permitAll()  // Token validated by ScimAuthFilter
```

A dedicated `ScimAuthFilter` extracts the Bearer token and validates it against the encrypted token stored in `ScimProviderConfig`. This keeps SCIM auth separate from LDAPAdmin's JWT-based admin auth.

#### Attribute Mapping

SCIM ↔ LDAP attribute mapping leverages the existing `ProfileAttributeConfig`:

| SCIM Attribute | Default LDAP Mapping | Configurable? |
|----------------|---------------------|---------------|
| `userName` | `uid` | Yes (via `userNameAttribute`) |
| `name.givenName` | `givenName` | Yes (via profile attribute config) |
| `name.familyName` | `sn` | Yes |
| `emails[type=work].value` | `mail` | Yes |
| `displayName` | `displayName` | Yes |
| `title` | `title` | Yes |
| `department` | `departmentNumber` or `ou` | Yes |
| `active` | Enable/disable action | Via `deprovisionAction` config |

The provisioning profile's `ProfileAttributeConfig` entries serve as the mapping table. A SCIM-specific overlay (`scim_attribute_mappings` or a JSON field on `ScimProviderConfig`) handles SCIM-specific paths like `emails[type=work].value`.

#### Integration with Existing Systems

| Existing System | Integration Point |
|----------------|-------------------|
| **Provisioning profiles** | SCIM user creation uses profile's target OU, object classes, RDN attribute, group assignments, and attribute validation |
| **Approval workflows** | Optional: SCIM creates can be routed through approval (configurable, default: auto-approve for SCIM) |
| **Lifecycle playbooks** | SCIM deactivation can trigger offboard playbook instead of simple disable |
| **Audit trail** | All SCIM operations recorded as audit events (SCIM_USER_CREATED, SCIM_USER_UPDATED, SCIM_USER_DEACTIVATED, SCIM_USER_DELETED, SCIM_GROUP_PUSHED) |
| **HR sync** | Complementary: HR sync detects orphans, SCIM prevents them. SCIM handles real-time provisioning; HR sync handles reconciliation. |
| **Access reviews** | SCIM-provisioned users appear in access review campaigns like any other LDAP user |

#### Flyway Migration

```sql
-- V47__scim_provisioning.sql

CREATE TABLE scim_provider_configs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id            UUID NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    profile_id              UUID REFERENCES provisioning_profiles(id) ON DELETE SET NULL,
    enabled                 BOOLEAN NOT NULL DEFAULT false,
    bearer_token_encrypted  VARCHAR(1024) NOT NULL,
    user_name_attribute     VARCHAR(64) NOT NULL DEFAULT 'uid',
    create_disabled_users   BOOLEAN NOT NULL DEFAULT false,
    delete_action           VARCHAR(10) NOT NULL DEFAULT 'DISABLE',
    group_push_enabled      BOOLEAN NOT NULL DEFAULT false,
    deprovision_action      VARCHAR(20) NOT NULL DEFAULT 'DISABLE',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (directory_id)
);

CREATE TABLE scim_resource_mappings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id    UUID NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    scim_id         VARCHAR(255) NOT NULL,
    external_id     VARCHAR(255),
    resource_type   VARCHAR(10) NOT NULL CHECK (resource_type IN ('User', 'Group')),
    ldap_dn         VARCHAR(2048) NOT NULL,
    user_name       VARCHAR(255),
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (directory_id, scim_id, resource_type)
);

CREATE INDEX idx_scim_mappings_dn ON scim_resource_mappings(directory_id, ldap_dn);
CREATE INDEX idx_scim_mappings_username ON scim_resource_mappings(directory_id, user_name)
    WHERE resource_type = 'User';

-- Add SCIM-related audit actions (handled by enum, but document here)
-- SCIM_USER_CREATED, SCIM_USER_UPDATED, SCIM_USER_DEACTIVATED, SCIM_USER_DELETED
-- SCIM_GROUP_CREATED, SCIM_GROUP_UPDATED, SCIM_GROUP_DELETED

-- Feature permissions
-- (SCIM config is superadmin-only, no separate feature key needed)
```

#### Frontend Changes

**SCIM Configuration Panel** (new section in directory settings or dedicated view):

- Enable/disable SCIM provisioning per directory
- Select provisioning profile (determines target OU, object classes, attribute mapping)
- Generate and rotate Bearer token (show once, then masked)
- Configure deprovisioning behavior (disable / delete / run playbook)
- Toggle group push
- Display Okta-facing URLs (base URL, endpoints) for copy-paste into Okta admin console
- View SCIM provisioning log (filtered audit events)

**SCIM User List** (optional, for debugging):
- Table of SCIM-mapped users: SCIM ID, userName, LDAP DN, active status, last synced
- Filter by active/inactive
- Manual re-sync button

---

## Effort Estimate

| Component | Size | Notes |
|-----------|------|-------|
| SCIM Controller (Users + Groups + discovery) | ~400 lines | SCIM JSON format is verbose |
| SCIM Provisioning Service | ~300 lines | Maps SCIM ↔ LDAP via existing services |
| SCIM Auth Filter | ~60 lines | Bearer token validation |
| SCIM DTOs (User, Group, ListResponse, Error, etc.) | ~200 lines | RFC 7643 compliance |
| SCIM Config entity + repository + migration | ~150 lines | Standard JPA pattern |
| SCIM resource mapping entity + repository | ~100 lines | ID ↔ DN tracking |
| SCIM management controller (config CRUD) | ~100 lines | Admin endpoints |
| Frontend SCIM config panel | ~250 lines | Single Vue component |
| Tests | ~400 lines | Controller + service tests |
| **Total** | **~2,000 lines** | |

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| **SCIM compliance gaps** — Okta's SCIM client is strict about response format | Use Okta's SCIM test suite (`runscope` bucket) during development; test with Okta's "SCIM 2.0 Test App" |
| **DN instability** — LDAP DNs change on move/rename, breaking SCIM ID mapping | `scim_resource_mappings` table decouples SCIM IDs from DNs; update mapping on USER_MOVE audit events |
| **Conflict with HR sync** — Both Okta SCIM and HR sync modify the same LDAP entries | Document recommended patterns: use Okta for provisioning, HR sync for read-only reconciliation. Add conflict detection (warn if SCIM and HR sync both write to same directory). |
| **Rate limiting** — Okta may burst many SCIM requests during bulk assignment | SCIM endpoints are stateless; LDAP connection pool handles concurrency. Add request rate limiting per Bearer token. |
| **Token rotation** — Compromised Bearer token grants full provisioning access | Support token rotation with grace period (old token valid for N hours after new token generated). Audit all SCIM auth failures. |
| **Large group push** — Groups with thousands of members | Implement SCIM PATCH for incremental member add/remove rather than full group replacement |

---

## Phased Delivery

### Phase 1: SCIM User Provisioning (core value)
- SCIM `/Users` endpoints (CRUD + search)
- Discovery endpoints (`ServiceProviderConfig`, `Schemas`, `ResourceTypes`)
- Bearer token auth
- Integration with provisioning profiles
- Audit logging
- Frontend config panel

### Phase 2: SCIM Group Push
- SCIM `/Groups` endpoints
- Group membership sync
- Conflict resolution with profile-based group assignments

### Phase 3: Advanced Lifecycle
- SCIM deactivation triggers offboard playbook
- Approval workflow integration for SCIM creates (optional gate)
- Okta app catalog submission (requires Okta ISV partnership)

### Phase 4: Multi-IdP (optional)
- Support multiple OIDC providers for admin SSO
- SCIM endpoints per IdP (Okta + Azure AD serving different directories)

---

## Recommendation

The SCIM provisioning server (Phase 1) delivers the highest ROI. It transforms LDAPAdmin from a standalone directory management tool into an **automation target** in the enterprise identity stack. The existing provisioning profile, audit, and lifecycle infrastructure means the SCIM controller is primarily a protocol adapter — the heavy lifting is already built.

Start with Phase 1 (SCIM Users), validate with Okta's test app, then proceed to Phase 2 (Groups) based on customer demand.
