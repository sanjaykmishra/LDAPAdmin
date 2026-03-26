# Auditor Portal — Development Milestones

Each milestone is a logical commit-and-test point: the codebase compiles, tests pass, and the feature is incrementally usable. Milestones are ordered so that each builds on the previous one with no dead code or orphaned files.

---

## Milestone 1 — Data Model & Entity Layer

**Commit: "Add auditor_links table and JPA entity"**

### What ships
- Flyway migration `V54__auditor_links.sql` — creates `auditor_links` table with all columns (token, scope, HMAC, expiry, tracking, revocation)
- Partial index on `token WHERE NOT revoked`
- `AuditorLink` JPA entity with Lombok annotations (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- `AuditorLinkRepository` with query methods: `findByTokenAndRevokedFalse(String token)`, `findByDirectoryIdOrderByCreatedAtDesc(UUID directoryId)`
- Three new `AuditAction` enum values: `AUDITOR_LINK_CREATED`, `AUDITOR_LINK_REVOKED`, `AUDITOR_LINK_ACCESSED`

### How to test
- `./mvnw test` — confirms migration runs on H2/test DB, entity maps correctly
- Write `AuditorLinkRepositoryTest`: save entity, find by token, verify partial-index-style query excludes revoked links

---

## Milestone 2 — CryptoService & AuditorLinkService

**Commit: "Add CryptoService and AuditorLinkService for token lifecycle"**

### What ships
- `CryptoService` — extracted from `EvidencePackageService`, exposes:
  - `generateToken()` — 256-bit `SecureRandom`, Base64URL-encoded
  - `hmacSha256(byte[] data)` — HMAC using app encryption key
  - `sha256Hex(byte[] data)` — SHA-256 checksum
- `EvidencePackageService` refactored to delegate to `CryptoService` (no behavior change)
- `AuditorLinkService` with methods:
  - `create(directoryId, scope, expiryDays, label, principal)` → generates token, computes HMAC over (token + directoryId + scope + expiresAt), records `AUDITOR_LINK_CREATED` audit event, returns `AuditorLinkDto` with shareable URL
  - `revoke(linkId, principal)` → sets `revoked=true`, `revokedAt=now()`, records `AUDITOR_LINK_REVOKED`
  - `list(directoryId)` → returns all links with access stats
  - `validateToken(token)` → checks exists + not revoked + not expired + HMAC matches; increments `accessCount`, updates `lastAccessedAt`; returns `AuditorLink` or throws `ResourceNotFoundException`
- DTOs: `CreateAuditorLinkRequest`, `AuditorLinkDto`, `AuditorLinkScopeDto`

### How to test
- `CryptoServiceTest` — token entropy (length, uniqueness), HMAC determinism, SHA-256 against known vectors
- `AuditorLinkServiceTest` — create happy path, revoke, validate valid/expired/revoked tokens, HMAC tamper detection
- `./mvnw test` — existing `EvidencePackageService` tests still pass after CryptoService extraction

---

## Milestone 3 — Admin CRUD Controller

**Commit: "Add authenticated AuditorLinkController for admin CRUD"**

### What ships
- `AuditorLinkController` (under `/api/v1/directories/{directoryId}/auditor-links`):
  - `POST /` — create link (scope, expiry, label)
  - `GET /` — list all links for directory
  - `DELETE /{linkId}` — revoke link
- `@RequiresFeature` annotation for access control (reuses existing feature permission pattern)
- Request validation: expiry 1-365 days, at least one scope flag true, label max 255 chars

### How to test
- `AuditorLinkControllerTest` — integration tests with `@WebMvcTest` or `@SpringBootTest`:
  - Create link → 201 with token in response
  - List links → includes created link
  - Revoke → 204, subsequent validate returns not found
  - Unauthorized access → 401
- `./mvnw test` — all tests green

---

## Milestone 4 — Public Portal API (Unauthenticated)

**Commit: "Add unauthenticated AuditorPortalController with rate limiting"**

### What ships
- `AuditorPortalController` — all routes under `/api/v1/auditor/{token}/...`:
  - `GET /` — portal metadata (label, directory name, scope, expiry, branding from ApplicationSettings)
  - `GET /campaigns` — campaign list with decision summaries
  - `GET /campaigns/{id}` — campaign detail + all decisions
  - `GET /sod` — SoD policies + violations
  - `GET /entitlements` — user-entitlement snapshot
  - `GET /audit-events` — audit log (scoped to `dataFrom`/`dataTo`)
  - `GET /approvals` — approval workflow history
  - `GET /verify` — returns HMAC verification result (algorithm, signature, covered fields, verified boolean)
  - `GET /export` — full ZIP download (reuses `EvidencePackageService`)
- `SecurityConfig` updated: `.requestMatchers("/api/v1/auditor/**").permitAll()`
- `IpRateLimiter` — new component for per-IP rate limiting on auditor routes (10 req/min), separate from the per-user `ApiRateLimiter`
- Response headers on all portal endpoints: `X-Robots-Tag: noindex`, `Cache-Control: no-store`
- All endpoints return 404 (not 401/403) for invalid/expired/revoked tokens
- `EvidencePackageService` data-fetching methods changed from `private` to package-private or extracted to `EvidenceDataService` so the portal controller can call them individually
- Per-section response DTOs: `PortalMetadataDto`, `PortalCampaignSummaryDto`, `PortalSodDto`, etc.

### How to test
- `AuditorPortalControllerTest`:
  - Valid token → 200 with correct data
  - Expired token → 404
  - Revoked token → 404
  - Invalid token → 404
  - Verify endpoint → returns `verified: true` for untampered link
  - Rate limit → 11th request in 60s returns 429
- `./mvnw test` — confirm no auth required on auditor routes, existing secured routes still require auth

---

## Milestone 5 — Portal Frontend: Shell, Router & Landing Dashboard

**Commit: "Add auditor portal Vue app with executive summary dashboard"**

### What ships
- New Vue pages under `frontend/src/views/auditor/`:
  - `AuditorLayout.vue` — standalone layout (no shared nav/auth with main app), professional muted blue/gray palette
  - `AuditorLanding.vue` — executive summary dashboard:
    - Headline metrics cards: total users reviewed, % decisions complete, SoD violations (open/resolved), privileged accounts count
    - Donut charts for decision breakdown and SoD status (lightweight — CSS-only or a small chart lib like Chart.js added to dependencies)
    - Compliance timeline: horizontal bar showing evidence window with key events
    - Scope summary with green checkmarks showing what's included
    - Expiry countdown banner
    - Organization branding (logo, name, primary color from `/api/v1/settings/branding`)
- Router entries in `frontend/src/router/index.js` for `/auditor/:token` routes (no auth guard)
- `frontend/src/api/auditorPortal.js` — API client for all `/api/v1/auditor/{token}/...` endpoints
- Ambient integrity verification:
  - Auto-calls `/verify` on page load
  - Persistent green shield badge in header: "Integrity Verified" / red if failed
  - Clicking badge opens a detail drawer showing HMAC algorithm, signature, covered fields

### How to test
- Manual: navigate to `/auditor/{valid-token}` → see dashboard with metrics, charts, verification badge
- Manual: navigate to `/auditor/invalid` → see 404 page
- `npm run build` — compiles without errors

---

## Milestone 6 — Portal Section Views: Campaigns & SoD

**Commit: "Add campaign and SoD section views with data tables"**

### What ships
- `AuditorCampaigns.vue` — campaign list page with decision summary per campaign
- `AuditorCampaignDetail.vue` — single campaign view with:
  - Decision table (member, decision, decided by, date) with search/filter
  - Campaign history timeline
  - Per-section SHA-256 checksum fingerprint with subtle checkmark
- `AuditorSod.vue` — SoD view with:
  - Policies table
  - Violations table with status color coding
  - Positive zero-state: "No Separation of Duties violations detected across N policies" (green success card)
- Sidebar navigation component with section links, active state highlighting
- Deep linking: stable anchor IDs on each row (`#decision-{id}`, `#violation-{id}`), copy-link icon per row
- Mobile responsive: sidebar collapses to hamburger menu on small screens, tables become card layouts

### How to test
- Manual: click through campaigns, SoD sections, verify data matches backend
- Manual: resize browser → confirm responsive layout
- Manual: copy deep link → paste in new tab → scrolls to correct row
- `npm run build` — compiles

---

## Milestone 7 — Portal Section Views: Audit Events, Entitlements & Approvals

**Commit: "Add audit events timeline, entitlements table, and approvals view"**

### What ships
- `AuditorAuditEvents.vue`:
  - Sortable data table (default view)
  - Toggle to timeline visualization: vertical chronological timeline, color-coded by action type, clustered related events
  - Search/filter by action type, date range, user
- `AuditorEntitlements.vue`:
  - User-entitlement table with group membership expansion
  - Search by user name, DN, group
- `AuditorApprovals.vue`:
  - Approval workflow history table
  - Status badges (approved, rejected, pending)
- Per-section checksum fingerprints on all new sections
- Activity transparency footer: "Access logged for compliance audit trail"

### How to test
- Manual: verify all three sections load data correctly
- Manual: toggle audit events between table and timeline view
- Manual: search/filter within each section
- `npm run build` — compiles

---

## Milestone 8 — Export & Print: CSV, PDF, and Workpaper Generation

**Commit: "Add per-section CSV/PDF export, print stylesheet, and audit workpaper"**

### What ships

#### Backend
- `AuditorExportController` endpoints (under `/api/v1/auditor/{token}/export/...`):
  - `GET /campaigns/{id}/csv` — campaign decisions CSV
  - `GET /campaigns/{id}/pdf` — branded PDF with logo, timestamps, HMAC signature in footer
  - `GET /sod/pdf` — SoD policies + violations PDF
  - `GET /audit-events/csv` — audit events CSV
  - `GET /audit-events/pdf` — audit events PDF
  - `GET /workpaper` — single combined PDF: table of contents, executive summary, all sections, page numbers, HMAC signature on every page footer
- Reuses existing `PdfReportService` patterns, extended with auditor-portal-specific templates

#### Frontend
- Export dropdown on each section header: "Export as CSV" / "Export as PDF"
- "Download Full ZIP" button (already wired from milestone 4)
- "Generate Audit Workpaper" button → downloads the combined PDF
- `@media print` stylesheet: clean paginated output, hides navigation/interactive elements, shows HMAC signature

### How to test
- Manual: download CSV per section → verify contents match displayed data
- Manual: download PDF per section → verify branding, timestamps, HMAC footer
- Manual: generate workpaper → verify TOC, all sections present, page numbers
- Manual: Ctrl+P → verify clean print output
- `./mvnw test` — PDF generation tests for auditor-specific templates
- `npm run build` — compiles

---

## Milestone 9 — Guided Walkthrough & Auditor Notes

**Commit: "Add guided evidence walkthrough and localStorage-based auditor notes"**

### What ships
- **Guided walkthrough mode**:
  - "Take a guided tour" button on landing page
  - Step-by-step navigation: "Step 1 of 5: Access Reviews" with Next/Previous buttons
  - Each step shows relevant data plus a brief explanation of what it demonstrates for compliance purposes
  - Exit guided mode anytime → returns to free navigation
  - Walkthrough state preserved in URL query param (`?guided=true&step=2`)
- **Auditor notes drawer**:
  - Slide-out drawer toggled by a "Notes" button in the header
  - Per-section text area that saves to `localStorage` (keyed by `token + section`)
  - "Copy section + notes" button: copies the section's key data plus the auditor's notes to clipboard
  - Clear "Your notes are stored locally and never sent to the server" disclaimer
  - Notes persist across page reloads but are scoped to the specific token

### How to test
- Manual: start guided walkthrough → step through all 5 steps → exit → verify free nav works
- Manual: add notes in campaigns section → reload → notes persist
- Manual: open different token → notes are independent
- Manual: "Copy section + notes" → paste into text editor → verify format
- `npm run build` — compiles

---

## Milestone 10 — Admin UX: Link Management UI

**Commit: "Add admin-side auditor link management UI"**

### What ships
- `AuditorLinksView.vue` — new admin view (authenticated):
  - "Share with Auditor" button → opens creation dialog:
    - Scope picker: checkboxes for campaigns (multi-select), SoD, entitlements, audit events
    - Date range picker for evidence window
    - Expiry selector: 7 / 14 / 30 / 60 / 90 days
    - Label field
  - On create: shows copyable URL with "Copy to Clipboard" button
  - Link management table: all links with columns for label, created by, created at, expires at, access count, last accessed, status (active/expired/revoked), revoke button
  - Confirmation dialog before revocation
- `frontend/src/api/auditorLinks.js` — API client for admin CRUD endpoints
- Router entry + sidebar nav item in admin app
- Feature permission check in the admin UI (hides if user lacks permission)

### How to test
- Manual: create link → copy URL → open in incognito → see auditor portal
- Manual: create link → revoke → try URL → see 404
- Manual: create multiple links → verify management table shows all with correct stats
- `npm run build` — compiles

---

## Milestone 11 — Visual Polish, Animations & Mobile Refinement

**Commit: "Polish auditor portal UI: animations, typography, responsive refinements"**

### What ships
- **Typography & spacing**: larger section headings, generous whitespace, clear data hierarchy across all portal views
- **Color palette finalization**: muted blues/grays for portal chrome, org's primary color for accents only
- **Animations**: subtle fade-in on section load (`transition` / `@keyframes`), smooth accordion expand/collapse for sidebar sections, verification badge pulse on first load
- **Positive zero-states everywhere**: replace "No X found" with green success cards ("No Separation of Duties violations detected across 12 policies", "All 150 users reviewed — 100% completion")
- **Mobile refinements**: test and fix all views at 320px, 768px, 1024px breakpoints; card layouts for all tables on mobile; touch-friendly tap targets
- **Loading states**: skeleton loaders on data sections while API calls complete
- **Error states**: friendly error cards if a section fails to load (not raw error messages)

### How to test
- Manual: visual review at desktop, tablet, and mobile breakpoints
- Manual: verify animations are smooth, not janky
- Manual: verify zero-states display correctly when data is empty
- Manual: slow network (DevTools throttle) → verify skeleton loaders appear
- `npm run build` — compiles

---

## Milestone 12 — Security Hardening & Final Integration Tests

**Commit: "Harden auditor portal security and add integration tests"**

### What ships
- **CSP headers** on portal responses: strict `Content-Security-Policy` allowing only self + CDN fonts/styles
- **CORS for auditor routes**: `Access-Control-Allow-Origin: *` only on `/api/v1/auditor/**`
- **robots.txt** exclusion: add `Disallow: /auditor/` rule
- **IP logging** on every portal access: stored in audit event metadata
- **Backend integration tests**:
  - Full lifecycle: create link → access portal → verify sections → revoke → confirm 404
  - Token expiry: create link with 0-day expiry → confirm immediate 404
  - HMAC tamper: modify link scope in DB → verify endpoint returns tamper detection
  - Rate limit: send 11 requests → confirm 429 on 11th
  - Scope enforcement: link with `includeSod=false` → `/sod` returns 404
- **Frontend smoke test**: ensure `npm run build` succeeds and all routes resolve

### How to test
- `./mvnw test` — all backend tests pass including new integration tests
- `npm run build` — frontend compiles
- Manual: full end-to-end walkthrough from admin link creation to auditor browsing to revocation
- Manual: verify response headers (CSP, X-Robots-Tag, Cache-Control) in browser DevTools

---

## Summary

| Milestone | Focus | Backend | Frontend | Key Test |
|-----------|-------|---------|----------|----------|
| 1 | Data model | Migration + Entity + Repo | — | DB migration runs, repo queries work |
| 2 | Core service | CryptoService + AuditorLinkService | — | Token lifecycle, HMAC verification |
| 3 | Admin API | AuditorLinkController (CRUD) | — | Create/list/revoke with auth |
| 4 | Public API | AuditorPortalController + rate limiter | — | Unauthenticated access, rate limiting |
| 5 | Portal shell | — | Layout + Dashboard + Verification | Executive summary renders, integrity badge |
| 6 | Campaigns & SoD | — | Section views + deep linking | Data tables, responsive, deep links |
| 7 | Events & more | — | Audit timeline + entitlements + approvals | Timeline toggle, search/filter |
| 8 | Export & print | PDF/CSV endpoints | Export UI + print CSS | PDF branding, workpaper generation |
| 9 | Guided UX | — | Walkthrough + notes | Step navigation, localStorage notes |
| 10 | Admin UI | — | Link management view | Create → copy → open → revoke flow |
| 11 | Polish | — | Animations + responsive + zero-states | Visual review at all breakpoints |
| 12 | Hardening | Security headers + integration tests | Build verification | Full lifecycle integration test |

**Total: 12 milestones, each independently committable and testable.**
