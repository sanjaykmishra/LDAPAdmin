# Auditor Portal — Implementation Plan

## Core Concept

A shareable, read-only web view of an evidence package that an auditor can access via a signed URL without needing an LDAPAdmin account. The link is time-scoped (e.g., 30 days), cryptographically signed to prevent tampering, and provides a browseable interface over the same data currently exported as a ZIP.

---

## Architecture

```
┌─────────────┐     signed URL      ┌──────────────────┐
│  Admin UI   │ ──── generates ────→ │  Auditor Portal  │
│  (authed)   │                      │  (no auth)       │
└─────────────┘                      └────────┬─────────┘
       │                                      │
       │ POST /auditor-links                  │ GET /auditor/{token}/...
       ▼                                      ▼
┌─────────────┐                      ┌──────────────────┐
│ AuditorLink │                      │  Same data as    │
│   table     │                      │  EvidencePackage │
│ (token,     │◄─────── reads ───────│  but rendered    │
│  scope,     │                      │  as HTML/JSON    │
│  expiry)    │                      └──────────────────┘
└─────────────┘
```

**Two separate controller paths:**
1. **Admin-side** (authenticated): Create/revoke/list auditor links
2. **Auditor-side** (unauthenticated): Browse evidence via signed token

---

## Data Model

**New entity: `AuditorLink`**

```sql
CREATE TABLE auditor_links (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id    UUID        NOT NULL REFERENCES directory_connections(id),
    token           VARCHAR(64) NOT NULL UNIQUE,  -- cryptographically random
    label           VARCHAR(255),                  -- "Q1 2026 SOC 2 Audit"

    -- Scope: what the auditor can see
    campaign_ids    UUID[],                        -- which campaigns
    include_sod     BOOLEAN     NOT NULL DEFAULT TRUE,
    include_entitlements BOOLEAN NOT NULL DEFAULT FALSE,
    include_audit_events BOOLEAN NOT NULL DEFAULT TRUE,

    -- Time bounds
    data_from       TIMESTAMPTZ,                   -- evidence window start
    data_to         TIMESTAMPTZ,                   -- evidence window end
    expires_at      TIMESTAMPTZ NOT NULL,           -- link expiry

    -- Signature
    hmac_signature  VARCHAR(64) NOT NULL,           -- HMAC of (token + scope + expiry)

    -- Tracking
    created_by      UUID        NOT NULL REFERENCES accounts(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_accessed_at TIMESTAMPTZ,
    access_count    INTEGER     NOT NULL DEFAULT 0,
    revoked         BOOLEAN     NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_auditor_links_token ON auditor_links (token) WHERE NOT revoked;
```

The token is a 256-bit cryptographically random value (Base64URL-encoded, ~43 chars). It's the only credential — possession of the token grants read access to the scoped evidence.

---

## Backend Components

### 1. `AuditorLinkService`

```
create(directoryId, scope, expiryDays, principal) → AuditorLink
  - Generates crypto-random token
  - Computes HMAC signature over (token + directoryId + scope + expiresAt)
  - Records audit event: AUDITOR_LINK_CREATED
  - Returns the link including the shareable URL

revoke(linkId, principal) → void
  - Sets revoked=true, records audit event

list(directoryId) → List<AuditorLinkDto>
  - Shows all links for the directory with access stats

validateToken(token) → AuditorLink or 404
  - Checks: exists, not revoked, not expired
  - Verifies HMAC signature matches (tamper detection)
  - Increments access_count, updates last_accessed_at
```

### 2. `AuditorPortalController` (unauthenticated)

All routes under `/auditor/{token}/...` — no Spring Security filter chain, no JWT required.

```
GET  /auditor/{token}                    → Portal landing page (metadata, sections)
GET  /auditor/{token}/campaigns          → Campaign list with decision summaries
GET  /auditor/{token}/campaigns/{id}     → Campaign detail + decisions
GET  /auditor/{token}/sod               → SoD policies + violations
GET  /auditor/{token}/entitlements      → User-entitlement snapshot
GET  /auditor/{token}/audit-events      → Audit log (within data_from/data_to)
GET  /auditor/{token}/approvals         → Approval workflow history
GET  /auditor/{token}/export            → Download full evidence ZIP
GET  /auditor/{token}/verify            → HMAC verification endpoint
```

Every endpoint checks `validateToken(token)` first — if expired/revoked/invalid, returns 404 (not 401/403, to avoid leaking that the token was ever valid).

### 3. Security considerations

- **No authentication needed** — the token IS the credential
- **Rate limiting** — per-IP rate limit on `/auditor/**` to prevent brute-force
- **Token entropy** — 256 bits = infeasible to guess (2^256 possibilities)
- **HMAC verification** — proves the link wasn't tampered with (scope/expiry modified)
- **Audit trail** — every access logged with IP, timestamp, user-agent
- **Revocation** — admin can kill a link immediately
- **Data isolation** — the portal queries are scoped to exactly what the link permits; no access to anything outside the defined scope
- **No write operations** — the entire controller is read-only
- **CORS** — allow `*` for auditor portal only (it's public by design)
- **CSP headers** — strict Content-Security-Policy on portal responses

---

## Frontend — Auditor Portal

A **separate, minimal Vue app** (or even server-rendered HTML) at `/auditor/{token}`. It doesn't share the main app's auth, router, or stores. Key characteristics:

- **No login screen** — landing page loads immediately from the token
- **Read-only** — no forms, no mutations, no state changes
- **Evidence browser** — left sidebar with sections (Campaigns, SoD, Entitlements, Audit Log, Approvals), each expandable
- **HMAC verification widget** — a prominent "Verify Integrity" button that calls `/auditor/{token}/verify` and displays the signature verification result with a green/red badge
- **Export buttons** — download individual sections as CSV/PDF or the full ZIP
- **Expiry countdown** — visible banner showing "This link expires in 23 days"
- **Branding** — uses the organization's configured app name and colors from ApplicationSettings

### Page layout

```
┌──────────────────────────────────────────────────────┐
│  [Company Logo]  Evidence Package for Q1 2026 Audit  │
│  Directory: Corporate LDAP  │  Expires: Apr 25 2026  │
│  [✓ Integrity Verified]              [Download ZIP]  │
├────────────┬─────────────────────────────────────────┤
│            │                                         │
│ ▸ Overview │  Campaign: Q1 Access Review             │
│ ▸ Campaigns│  Status: CLOSED  │  Completed: Mar 15   │
│   ▸ Q1    │  ─────────────────────────────────────   │
│   ▸ Q2    │  Decisions: 142 confirmed, 8 revoked     │
│ ▸ SoD     │  Completion: 100%                        │
│ ▸ Entitle.│                                          │
│ ▸ Audit   │  ┌──────┬────────┬──────┬───────────┐   │
│ ▸ Approvals│ │Member│Decision│By    │Date       │   │
│            │  ├──────┼────────┼──────┼───────────┤   │
│            │  │jdoe  │CONFIRM │admin │2026-03-10 │   │
│            │  │asmith│REVOKE  │admin │2026-03-11 │   │
│            │  └──────┴────────┴──────┴───────────┘   │
│            │                         [Export CSV]     │
└────────────┴─────────────────────────────────────────┘
```

---

## Admin-Side UX (within existing app)

In the Evidence Package section or as a new top-level "Auditor Links" page:

1. **"Share with Auditor" button** on the evidence package page
2. **Scope picker** — checkboxes for what to include (campaigns, SoD, entitlements, audit events), date range
3. **Expiry selector** — 7 / 14 / 30 / 60 / 90 days
4. **Label field** — "Q1 2026 SOC 2 Audit — Deloitte"
5. **Generate link** → shows a copyable URL with a "Copy to Clipboard" button
6. **Link management table** — all active links with access count, last accessed, revoke button

---

## Implementation Plan

### Phase 1 — Backend (3-4 days)
- Migration: `auditor_links` table
- Entity + Repository
- `AuditorLinkService`: create, revoke, list, validate
- `AuditorLinkController` (admin-side, authenticated): CRUD endpoints
- `AuditorPortalController` (public, unauthenticated): read-only endpoints
- Spring Security config: exclude `/auditor/**` from auth filter chain
- Rate limiter on portal routes
- Audit events: `AUDITOR_LINK_CREATED`, `AUDITOR_LINK_REVOKED`, `AUDITOR_LINK_ACCESSED`

### Phase 2 — Auditor Portal Frontend (3-4 days)
- Minimal standalone Vue app or server-rendered pages at `/auditor/{token}`
- Section browser (campaigns, SoD, entitlements, audit log, approvals)
- Data tables with search/filter
- HMAC verification widget
- Export per-section (CSV/PDF) and full ZIP download
- Expiry banner, branding from settings

### Phase 3 — Admin UX (1-2 days)
- "Share with Auditor" flow in evidence package page
- Link management table (active links, access stats, revoke)
- Email integration: optionally email the link directly to the auditor

**Total: ~8-10 days**

---

## What Makes This Hard to Replicate

1. **The HMAC verification is auditor-facing** — the auditor can independently verify the evidence hasn't been tampered with, which is something they currently have to take on faith with ZIP files
2. **The scoping is precise** — the admin controls exactly what the auditor sees, with time bounds, so there's no risk of exposing irrelevant data
3. **The access audit trail** — the admin can see exactly when the auditor accessed which sections, which is itself compliance evidence
4. **Zero-friction for the auditor** — no account creation, no password, no VPN, just a link. Auditors are notoriously impatient with vendor tools

---

## Risk Considerations

| Risk | Mitigation |
|------|-----------|
| Token leaked/forwarded | Revocation + expiry + access logging; IP logging helps detect sharing |
| Brute force token guessing | 256-bit entropy + per-IP rate limiting (10 req/min) |
| Data exposure after employee leaves | Admin can revoke all links; links auto-expire |
| Stale evidence (data changes after link created) | Link shows data as of creation time OR live-queries with time bounds — decision needed |
| SEO indexing of portal | `X-Robots-Tag: noindex` + `robots.txt` exclusion |

### Stale vs. Live Data Decision

The **stale vs. live** question is the key architectural decision. Two options:

- **Snapshot model**: When the link is created, freeze the evidence as a stored JSON/ZIP blob. The auditor always sees the same data regardless of when they access it. Simpler, more deterministic, but requires storage.
- **Live query model**: The portal queries live data with the `data_from`/`data_to` time bounds. The auditor sees current state within those bounds. No storage needed, but data could change between accesses (e.g., a violation gets resolved).

**Recommendation**: Live query with time bounds for campaigns/SoD/audit events (they have timestamps), and snapshot at creation for the entitlements section (LDAP data has no reliable timestamp). The manifest HMAC would cover the snapshot portions.

---

## Prerequisite Assessment

The current codebase has all prerequisites. No architectural changes required.

| Prerequisite | Status | What Exists |
|---|---|---|
| Public route pattern | ✅ Ready | `SecurityConfig` already has `.permitAll()` for branding, auth, self-service — just add `/api/v1/auditor/**` |
| Rate limiting | ✅ Ready | `ApiRateLimiter` with per-key throttling; usable for per-IP on auditor routes |
| Individual data fetchers | ✅ Ready | `EvidencePackageService` has `addCampaignData()`, `addSodData()`, `addAuditEvents()`, `addUserEntitlements()`, `addApprovalHistory()` — all independent, each with their own error handling. Currently `private`, just need to be made `public` |
| HMAC signing | ✅ Ready | `hmacSha256()` in `EvidencePackageService` uses the app's encryption key, self-contained, extractable to a shared service |
| SHA-256 checksums | ✅ Ready | `sha256Hex()` already exists |
| Branding for portal styling | ✅ Ready | `appName`, `logoUrl`, `primaryColour`, `secondaryColour` all exposed via the public `/api/v1/settings/branding` endpoint |
| Audit trail | ✅ Ready | `AuditService.record()` is async, fire-and-forget, with `AuditAction` enum easily extendable |
| Frontend build | ✅ Ready | Decoupled Vite + nginx architecture — auditor portal can be a separate Vue app or a set of routes in the existing app |
| Token/crypto infrastructure | ✅ Ready | `EncryptionService` handles AES-256, `AppProperties` provides the key, `java.security.SecureRandom` available for token generation |

### What's Needed (All Additive, No Refactoring)

1. **One migration** — `auditor_links` table
2. **One entity** — `AuditorLink`
3. **One service** — `AuditorLinkService` (create, revoke, validate)
4. **Two controllers** — admin CRUD + public portal endpoints
5. **One line in SecurityConfig** — `.requestMatchers("/api/v1/auditor/**").permitAll()`
6. **Visibility change** — Make 5 methods in `EvidencePackageService` `public` instead of `private` (or extract to a shared `EvidenceDataService`)
7. **Extract HMAC** — Move `hmacSha256()` to a shared `CryptoService` (or just inject `EvidencePackageService` into the new service)
8. **Frontend** — New Vue pages under `/auditor/{token}` served by the existing nginx config
