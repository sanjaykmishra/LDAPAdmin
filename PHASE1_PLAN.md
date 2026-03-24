# Phase 1 Implementation Plan: Make It Buyable
## Item 1.4: Group Picker in Campaign Creation (Fastest Win)
**Status:** GroupDnPicker component already exists and works in profiles/playbooks.
**Changes:**
- `frontend/src/views/accessReviews/CampaignCreateView.vue`: Replace the plain text `<input>` for `groupDn` (line ~66) with `<GroupDnPicker v-model="g.groupDn" :directory-id="dirId" />`
- Import GroupDnPicker component
- No backend changes needed
---
## Item 1.5: SIEM / Syslog Export
**Current state:** AuditService records events async. AuditEvent entity has 13+ fields. No external sink exists.
### Backend Changes:
1. **Flyway migration (V38):** Add SIEM config columns to `application_settings`:
   - `siem_enabled`, `siem_protocol` (SYSLOG_UDP, SYSLOG_TCP, SYSLOG_TLS, WEBHOOK), `siem_host`, `siem_port`, `siem_format` (RFC5424, CEF, JSON), `siem_auth_token_encrypted`, `webhook_url`, `webhook_auth_header_encrypted`
2. **Update ApplicationSettings entity** with new SIEM fields
3. **Update DTOs** (ApplicationSettingsDto, UpdateApplicationSettingsRequest) to include SIEM config
4. **New: `SiemExportService`** — Listens for audit events and forwards them:
   - `@Async` method called from AuditService after recording
   - Formats event based on configured format (RFC 5424 / CEF / JSON)
   - Sends via configured protocol (UDP syslog, TCP syslog, HTTPS webhook)
   - Fire-and-forget with error logging (no dead-letter queue for Phase 1)
5. **New: `SiemFormatter`** — Converts AuditEvent to output format:
   - RFC 5424 syslog message format
   - CEF (Common Event Format) string
   - JSON (structured, uses existing Jackson serialization)
6. **New: `SiemClient`** — Transport layer:
   - UDP/TCP syslog sender (Java DatagramSocket / Socket)
   - Webhook sender (Spring RestClient with retry)
7. **Update `AuditService`** — After recording, fire async SIEM export if enabled
8. **Test endpoint:** `POST /api/v1/superadmin/settings/siem/test` — sends a test event
### Frontend Changes:
9. **Update settings view** (ApplicationSettingsView or create SIEM section):
   - Protocol selector, host/port, format, auth token
   - Webhook URL field (shown when protocol=WEBHOOK)
   - Test connection button
   - Enable/disable toggle
---
## Item 1.3: Compliance Report Templates (PDF)
**Current state:** CSV export works. ReportExecutionService throws UnsupportedOperationException for PDF. No PDF library.
### Backend Changes:
1. **Add OpenPDF dependency** to pom.xml (LGPL, fork of iText 4 — no licensing issues)
2. **New: `PdfReportService`** — Generates styled PDF reports with:
   - Company branding header (from ApplicationSettings appName/logoUrl)
   - Generated timestamp, report metadata
   - Tabular data with pagination
3. **Three report implementations:**
   a. **User Access Report** — Who has what:
      - Input: directoryId, optional groupDn filter
      - Data: LDAP group memberships via LdapGroupService
      - Output: Table of users with their group memberships, grouped by group
   b. **Access Review Summary** — Campaign decisions:
      - Input: campaignId
      - Data: AccessReviewDecision records (already queryable)
      - Output: Campaign metadata + decision table (confirm/revoke/pending counts, per-group breakdown, reviewer actions)
   c. **Privileged Account Inventory** — Admin accounts:
      - Input: none (global)
      - Data: Account + AdminProfileRole + AdminFeaturePermission
      - Output: Table of admin accounts with their roles, profile access, feature permissions, last login
4. **New controller:** `ComplianceReportController` at `/api/v1/directories/{dirId}/compliance-reports`:
   - `GET /user-access?format=pdf` — User Access Report
   - `GET /access-review-summary/{campaignId}?format=pdf` — Access Review Summary
   - `GET /privileged-accounts?format=pdf` — Privileged Account Inventory (global, under `/api/v1/compliance-reports/`)
5. **Update ReportExecutionService** — Wire PDF format to PdfReportService for scheduled jobs
### Frontend Changes:
6. **New view or section:** Compliance Reports page accessible from sidebar:
   - Three report cards with description
   - Parameter selection (directory, campaign, date range)
   - Generate & download PDF button
   - Option to schedule recurring generation
---
## Item 1.1: First-Run Setup Wizard
**Current state:** No wizard. 9 manual steps. BootstrapService creates superadmin on first run.
### Backend Changes:
1. **Flyway migration:** Add `setup_completed` boolean to `application_settings` (default false)
2. **Update ApplicationSettings entity** with `setupCompleted` field
3. **New endpoint:** `GET /api/v1/auth/setup-status` (public, no auth required):
   - Returns `{ setupCompleted: boolean }`
   - Used by frontend to decide whether to show wizard
4. **Update ApplicationSettingsDto** to include `setupCompleted`
### Frontend Changes:
5. **New view: `SetupWizardView.vue`** — Multi-step guided setup:
   - **Step 1: Welcome** — Product intro, what we'll configure
   - **Step 2: Connect LDAP** — Host, port, SSL mode, bind DN/password, base DN fields (reuse DirectoriesManageView form logic). Test connection button with real-time feedback.
   - **Step 3: Verify Connection** — Show discovered user/group counts, sample entries. Confirm base DNs are correct.
   - **Step 4: Create Profile** — Simplified provisioning profile (name, target OU, basic attributes). Use schema discovery to suggest object classes and RDN attribute.
   - **Step 5: First Access Review** — Create a campaign: pick groups (using GroupDnPicker), set deadline, assign self as reviewer.
   - **Step 6: Done** — Summary of what was configured. Links to quick-start guide, dashboard, and first campaign.
6. **Router update:** If `setupCompleted === false` and user is superadmin, redirect to `/setup` after login
7. **Mark complete:** Final wizard step calls `PUT /api/v1/superadmin/settings` with `setupCompleted: true`
---
## Item 1.2: Admin Quick-Start Guide
**Type:** Documentation only — a single markdown/HTML page.
This is a docs task. Can be implemented as:
- A static page served by the frontend (`/guide`)
- Or a markdown file in the repo that gets linked from the wizard's final step
---
## Implementation Order
1. **1.4 Group picker** (~30 min) — Immediate win, unblocks campaign creation UX
2. **1.5 SIEM export** (~1-2 days) — Self-contained integration, no dependencies
3. **1.3 PDF reports** (~1-2 days) — Needs PDF library, three report templates
4. **1.1 Setup wizard** (~2-3 days) — Largest item, builds on 1.4's group picker
5. **1.2 Quick-start guide** (~1 day) — Write after wizard is done so it can reference the UI
