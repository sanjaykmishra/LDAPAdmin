# Okta Integration Plan — Critical Review

## Context

This document examines whether a deeper Okta integration makes sense for LDAPAdmin, what it would look like if pursued, and a recommendation on whether to proceed.

**Current state:** LDAPAdmin already supports Okta as a login provider via its generic OIDC implementation (`OidcAuthenticationService.java`). Admins can configure Okta's issuer URL, client ID, and client secret — and Okta users can authenticate into LDAPAdmin's admin console.

**The question:** Should LDAPAdmin go further — reading Okta users, groups, and application assignments as an additional access source for governance and access reviews?

---

## The Strategic Tension

`high_value_integrations.md` explicitly recommends against Okta integration:

> **Okta / Auth0** — These are identity providers, not access sources — if the customer has Okta, they probably don't need LDAPAdmin.

This is directionally correct **for Okta's core use case** (SSO federation), but it misses a real-world scenario: **hybrid environments where Okta coexists with legacy LDAP**.

### When Okta + LDAP Coexist

Many mid-size organizations (500–5,000 employees) have:

1. **Okta** — SSO for cloud apps (Salesforce, Slack, AWS Console, etc.)
2. **LDAP** — On-prem apps that predate the Okta rollout (Jenkins, Nexus, internal tools, network equipment, VPN concentrators)

These orgs chose Okta for cloud but **never migrated their on-prem LDAP stack**. The LDAP directory is not going away — it's embedded in infrastructure. Okta may even be configured with an LDAP agent (Okta AD/LDAP Interface) that delegates authentication to the LDAP directory.

**In this scenario, LDAPAdmin is still the right tool for LDAP governance.** The question is whether pulling Okta data makes access reviews more complete.

---

## What a Read-Only Okta Integration Would Look Like

### Scope: Okta as an Access Source (Not an IdP)

This integration would **not** replace the existing OIDC login flow. It would treat Okta as an additional data source for access reviews — similar to how Google Workspace is planned.

**Data to import (read-only):**
- Okta users (status, profile, last login)
- Okta groups (membership, type: Okta-managed vs. LDAP-sourced)
- Okta application assignments (which users have access to which apps)

**Governance value:**
- "Show me all access for user X — both LDAP groups and Okta app assignments"
- "Which Okta users have no corresponding LDAP account?" (identity mismatch detection)
- "Which LDAP users have Okta accounts in SUSPENDED/DEPROVISIONED state?" (orphan detection from the Okta direction)
- Include Okta app assignments in access review campaigns alongside LDAP group memberships

---

## Architecture (If Pursued)

Following the established integration pattern (BambooHR plan, SIEM export):

```
Okta API ──────► OktaClient ──────► OktaSyncService
                     │                     │
                     │                ┌────┴────┐
                     │                ▼         ▼
                OktaUser          OktaSyncRun  AuditService
                OktaGroup         (entity)
                OktaAppAssignment
                (entities)
                     │
                     ▼
              Identity Matching
              (email → LDAP DN)
                     │
                ┌────┴────────────┐
                ▼                 ▼
          Cross-source       Mismatch
          access view        detection
```

### Step 1: Database Migration (V46__okta_integration.sql)

```sql
CREATE TABLE okta_connections (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id      UUID NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    display_name      VARCHAR(200) NOT NULL,
    enabled           BOOLEAN NOT NULL DEFAULT false,

    -- Okta config
    okta_domain       VARCHAR(500) NOT NULL,   -- e.g. "acme.okta.com"
    api_token_encrypted TEXT NOT NULL,          -- AES-256 GCM encrypted SSWS token

    -- Identity matching
    match_attribute   VARCHAR(100) NOT NULL DEFAULT 'mail',
    match_field       VARCHAR(100) NOT NULL DEFAULT 'email',

    -- Sync config
    sync_cron         VARCHAR(50) NOT NULL DEFAULT '0 0 * * * ?',
    sync_users        BOOLEAN NOT NULL DEFAULT true,
    sync_groups       BOOLEAN NOT NULL DEFAULT true,
    sync_app_assignments BOOLEAN NOT NULL DEFAULT false,  -- expensive, opt-in

    last_sync_at      TIMESTAMPTZ,
    last_sync_status  VARCHAR(20),
    last_sync_message TEXT,

    created_by        UUID REFERENCES accounts(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(directory_id)
);

CREATE TABLE okta_users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    okta_connection_id UUID NOT NULL REFERENCES okta_connections(id) ON DELETE CASCADE,
    okta_id           VARCHAR(100) NOT NULL,
    login             VARCHAR(500),
    email             VARCHAR(500),
    first_name        VARCHAR(200),
    last_name         VARCHAR(200),
    status            VARCHAR(50) NOT NULL,     -- ACTIVE, STAGED, PROVISIONED, SUSPENDED, DEPROVISIONED, etc.
    last_login        TIMESTAMPTZ,

    matched_ldap_dn   VARCHAR(1000),
    match_confidence  VARCHAR(20),

    last_synced_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(okta_connection_id, okta_id)
);

CREATE INDEX idx_okta_users_connection ON okta_users(okta_connection_id);
CREATE INDEX idx_okta_users_status ON okta_users(okta_connection_id, status);
CREATE INDEX idx_okta_users_matched ON okta_users(okta_connection_id, matched_ldap_dn);

CREATE TABLE okta_groups (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    okta_connection_id UUID NOT NULL REFERENCES okta_connections(id) ON DELETE CASCADE,
    okta_id           VARCHAR(100) NOT NULL,
    name              VARCHAR(500) NOT NULL,
    description       TEXT,
    type              VARCHAR(50),              -- OKTA_GROUP, APP_GROUP, BUILT_IN

    last_synced_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(okta_connection_id, okta_id)
);

CREATE TABLE okta_group_members (
    okta_group_id     UUID NOT NULL REFERENCES okta_groups(id) ON DELETE CASCADE,
    okta_user_id      UUID NOT NULL REFERENCES okta_users(id) ON DELETE CASCADE,
    PRIMARY KEY (okta_group_id, okta_user_id)
);

CREATE TABLE okta_app_assignments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    okta_connection_id UUID NOT NULL REFERENCES okta_connections(id) ON DELETE CASCADE,
    okta_user_id      UUID NOT NULL REFERENCES okta_users(id) ON DELETE CASCADE,
    app_id            VARCHAR(100) NOT NULL,
    app_label         VARCHAR(500),
    app_status        VARCHAR(50),

    last_synced_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(okta_connection_id, okta_user_id, app_id)
);

CREATE TABLE okta_sync_runs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    okta_connection_id UUID NOT NULL REFERENCES okta_connections(id) ON DELETE CASCADE,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    total_users       INTEGER,
    total_groups      INTEGER,
    total_app_assignments INTEGER,
    matched_count     INTEGER DEFAULT 0,
    unmatched_count   INTEGER DEFAULT 0,
    mismatch_count    INTEGER DEFAULT 0,   -- Okta deprovisioned but LDAP active
    error_message     TEXT,
    triggered_by      VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED'
);
```

### Step 2: Okta API Client

**File:** `src/main/java/com/ldapadmin/service/okta/OktaClient.java`

**Okta API endpoints:**
- `GET /api/v1/users` — paginated (200 per page, Link header pagination)
- `GET /api/v1/groups` — all groups
- `GET /api/v1/groups/{groupId}/users` — group membership
- `GET /api/v1/apps/{appId}/users` — app assignments (or `/api/v1/users/{userId}/appLinks`)

**Auth:** `Authorization: SSWS {api_token}` header.

**Rate limits:** Okta rate limits vary by endpoint (typically 600 req/min for /users, 500 req/min for /groups). Pagination is required — Okta uses cursor-based pagination via Link headers.

```java
public class OktaClient {
    List<OktaUser> fetchAllUsers(String domain, String apiToken)
    List<OktaGroup> fetchAllGroups(String domain, String apiToken)
    List<OktaUser> fetchGroupMembers(String domain, String apiToken, String groupId)
    List<OktaAppLink> fetchUserAppLinks(String domain, String apiToken, String userId)
    boolean testConnection(String domain, String apiToken)
}
```

**Key complexity:** Okta pagination. Unlike BambooHR (which returns all employees in one call), Okta requires following `Link: <url>; rel="next"` headers across multiple requests. For an org with 5,000 users, this means ~25 paginated requests just for users.

### Step 3: Sync Service

**File:** `src/main/java/com/ldapadmin/service/okta/OktaSyncService.java`

Flow mirrors BambooHR sync:
1. Create `OktaSyncRun` (RUNNING)
2. Fetch all Okta users (paginated)
3. Upsert into `okta_users`
4. Fetch groups + membership (if enabled)
5. Fetch app assignments (if enabled — this is the expensive one)
6. Run identity matching (email → LDAP DN)
7. Detect mismatches: Okta DEPROVISIONED/SUSPENDED but LDAP account active
8. Update sync run with counts
9. Audit log

### Step 4: Controller

**Base path:** `/api/v1/directories/{directoryId}/okta`

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| `GET` | `/` | `OKTA_VIEW` | Get Okta connection config |
| `POST` | `/` | `OKTA_MANAGE` | Create connection |
| `PUT` | `/` | `OKTA_MANAGE` | Update connection |
| `DELETE` | `/` | `OKTA_MANAGE` | Remove connection |
| `POST` | `/test` | `OKTA_MANAGE` | Test API connectivity |
| `POST` | `/sync` | `OKTA_MANAGE` | Trigger manual sync |
| `GET` | `/sync-history` | `OKTA_VIEW` | Sync run history |
| `GET` | `/users` | `OKTA_VIEW` | List Okta users (filterable) |
| `GET` | `/groups` | `OKTA_VIEW` | List Okta groups |
| `GET` | `/mismatches` | `OKTA_VIEW` | Users with status mismatches |
| `GET` | `/summary` | `OKTA_VIEW` | Dashboard widget data |

### Step 5: Frontend

Two new views following the BambooHR pattern:
- `OktaConnectionView.vue` — connection setup, test, sync trigger
- `OktaUsersView.vue` — user list with tabs: All / Active / Deprovisioned / Mismatched

### Step 6: Access Review Integration

**This is the hard part.** To be useful, Okta app assignments need to appear alongside LDAP group memberships in access review campaigns. This requires:

- Modifying `AccessReviewItem` to support non-LDAP access sources
- Adding a source type field (LDAP_GROUP, OKTA_APP, OKTA_GROUP)
- Modifying the review UI to show heterogeneous access items
- Modifying campaign creation to optionally include Okta data

This is a significant refactor of the access review model, which currently assumes all reviewed access is LDAP group membership.

---

## Effort Estimate

| Component | Effort |
|-----------|--------|
| Database migrations | 1 day |
| Entities, enums, repositories, DTOs | 1 day |
| OktaClient (with pagination) | 2 days |
| OktaSyncService + scheduler | 2 days |
| Controller + tests | 2 days |
| Frontend views | 2 days |
| **Subtotal (standalone)** | **~2 weeks** |
| Access review model refactor | 1–2 weeks additional |
| **Total (with review integration)** | **3–4 weeks** |

---

## Critical Review

### Arguments FOR the Integration

1. **Hybrid environments are real.** Many mid-size orgs run Okta + LDAP simultaneously. An admin managing LDAP access reviews would benefit from seeing the Okta side too.

2. **Orphan detection from both directions.** BambooHR tells you "this person was terminated." Okta tells you "this person's SSO was deprovisioned." If the LDAP account is still active, that's a finding either way. Having both signals is more robust than either alone.

3. **Market differentiation.** No lightweight IGA tool covers both LDAP and Okta in a unified review. Enterprise tools (SailPoint, Saviynt) do, but at 10x the cost.

4. **Minimal risk.** Read-only integration. Never writes to Okta. If it breaks, access reviews still work — you just lose the Okta visibility.

### Arguments AGAINST the Integration

1. **Product positioning conflict.** The original analysis is right: if a customer has Okta, they're often moving *away* from LDAP. Building Okta integration risks chasing a market that's shrinking for your core product.

2. **Access review model refactor is expensive.** The standalone sync (users, groups, app assignments into local tables) is straightforward. But making Okta data *actionable* in access reviews requires restructuring the review model from "LDAP groups" to "multi-source access items." That's architectural, not incremental.

3. **Okta already has governance features.** Okta Identity Governance (OIG) provides access reviews, certifications, and lifecycle management natively. Customers who buy Okta OIG won't need LDAPAdmin for that side. You'd be competing with Okta's own product for their own data.

4. **API token management is a security concern.** Okta SSWS tokens are long-lived and powerful — they grant read access to all users, groups, and app assignments. Storing this in LDAPAdmin's database (even encrypted) expands the blast radius. If LDAPAdmin is compromised, the attacker gets a full Okta directory dump. This is different from BambooHR (where the blast radius is employee names/emails) — Okta tokens expose the entire access graph.

5. **Effort vs. impact ratio is unfavorable.** BambooHR integration (2–3 weeks) answers the #1 compliance question: "should this person have access at all?" Okta integration (3–4 weeks) answers a less critical question: "what other access does this person have?" — and only for the subset of customers running both Okta and LDAP.

6. **Pagination complexity.** Unlike BambooHR (one API call), Okta requires dozens of paginated requests with cursor tracking and rate limit handling. The `OktaClient` would be significantly more complex than `BambooHrClient`, adding maintenance burden.

---

## Recommendation: Do NOT Build This Now

The Okta integration should remain on the **"not now"** list for the following reasons:

### Priority Order
1. **BambooHR integration** (already planned, higher impact) — answers identity lifecycle
2. **Google Workspace** (already planned, similar effort) — more common hybrid scenario than Okta + LDAP
3. **ServiceNow/Jira ticketing** — improves workflow stickiness

Okta integration would be **Phase 4 at earliest**, contingent on:
- Customer demand (at least 3 paying customers requesting it)
- BambooHR and Google Workspace integrations being complete
- Access review model already refactored for multi-source (which Google Workspace would require first)

### If You Do Build It Later

The Google Workspace integration will force the access review model refactor (multi-source items). Once that refactor exists, adding Okta becomes cheaper — roughly 2 weeks instead of 3–4 weeks, since the hardest part (review model) is already done.

**Recommended sequencing:**
```
BambooHR (identity lifecycle) → Google Workspace (forces multi-source refactor)
    → Okta (leverages multi-source model, lower incremental cost)
```

### What to Do Instead Right Now

The existing OIDC implementation already supports Okta as a login provider. If customers ask for "Okta integration," clarify what they mean:

- **"I want my admins to log in via Okta"** → Already supported. Point them to OIDC configuration.
- **"I want to see Okta assignments in access reviews"** → Log the request. Revisit in Phase 4.
- **"I want Okta to be the identity lifecycle source"** → BambooHR integration is the better answer. The HR system is the authoritative source, not the IdP. Okta gets its data from HR too.

---

## File Summary (If Built)

### New Files (~28)

| Layer | Files |
|-------|-------|
| **Migration** | `V46__okta_integration.sql`, `V47__okta_feature_permissions.sql` |
| **Entities** | `OktaConnection.java`, `OktaUser.java`, `OktaGroup.java`, `OktaGroupMember.java`, `OktaAppAssignment.java`, `OktaSyncRun.java` |
| **Enums** | `OktaUserStatus.java`, `OktaSyncStatus.java` (reuse `HrSyncTrigger`, `HrMatchConfidence`) |
| **Repositories** | `OktaConnectionRepository.java`, `OktaUserRepository.java`, `OktaGroupRepository.java`, `OktaAppAssignmentRepository.java`, `OktaSyncRunRepository.java` |
| **DTOs** | `OktaConnectionDto.java`, `CreateOktaConnectionRequest.java`, `UpdateOktaConnectionRequest.java`, `OktaUserDto.java`, `OktaGroupDto.java`, `OktaAppAssignmentDto.java`, `OktaSyncRunDto.java`, `OktaSyncSummaryDto.java`, `OktaMismatchDto.java`, `OktaTestConnectionResponse.java` |
| **Services** | `OktaClient.java`, `OktaSyncService.java`, `OktaSyncScheduler.java` |
| **Controller** | `OktaConnectionController.java` |
| **Frontend** | `oktaIntegration.js`, `OktaConnectionView.vue`, `OktaUsersView.vue` |
| **Tests** | `OktaClientTest.java`, `OktaSyncServiceTest.java`, `OktaConnectionControllerTest.java` |

### Modified Files (~5)

| File | Change |
|------|--------|
| `AuditAction.java` | Add `OKTA_SYNC_STARTED`, `OKTA_SYNC_COMPLETED`, `OKTA_SYNC_FAILED`, `OKTA_MISMATCH_DETECTED` |
| `FeatureKey.java` | Add `OKTA_MANAGE`, `OKTA_VIEW` |
| `application.yml` | Add `ldapadmin.okta.*` config block |
| `frontend/src/router/index.js` | Add Okta routes |
| Sidebar nav component | Add Okta nav item |

---

## Conclusion

The technical implementation is straightforward — it follows the same pattern as BambooHR and SIEM integrations. The question is not "can we build it" but "should we build it now." The answer is **no**. The BambooHR integration delivers more compliance value, the Google Workspace integration addresses a more common hybrid scenario, and both of those should ship first. Okta integration becomes significantly cheaper after Google Workspace forces the multi-source access review refactor.

The existing OIDC login support already covers the most common Okta ask. Deeper integration is a Phase 4 item, gated on demonstrated customer demand.
