# Phase 2 Implementation Plan — "Make It Sticky"

**Goal:** Customers who complete their first access review have reasons to stay and automate. An auditor can receive a single evidence package covering access reviews, SoD policies, and approval history.

**Timeline:** Weeks 5–10 (6 weeks)

---

## Feature 2.1: Separation of Duties (SoD) Policy Engine

**Effort:** 2 weeks | **Priority:** High | **Dependencies:** None

### What Exists
Nothing — this is a greenfield feature.

### What to Build

#### Backend

1. **Database migrations (V40, V41)**
   - `V40__sod_policies.sql`
     - `sod_policies` table: `id (UUID)`, `name`, `description`, `directory_id (FK)`, `group_a_dn`, `group_b_dn`, `group_a_name`, `group_b_name`, `severity (ENUM: HIGH, MEDIUM, LOW)`, `action (ENUM: ALERT, BLOCK)`, `enabled (boolean)`, `created_by (FK)`, `created_at`, `updated_at`
     - `sod_violations` table: `id (UUID)`, `policy_id (FK)`, `user_dn`, `user_display_name`, `detected_at`, `resolved_at`, `status (ENUM: OPEN, RESOLVED, EXEMPTED)`, `exempted_by (FK)`, `exemption_reason`
   - `V41__sod_feature_permissions.sql`
     - Add `sod.manage` and `sod.view` to `feature_keys` constraint

2. **Entities**
   - `SodPolicy.java` — JPA entity mapping `sod_policies`
   - `SodViolation.java` — JPA entity mapping `sod_violations`
   - Enums: `SodSeverity`, `SodAction`, `SodViolationStatus`

3. **Repositories**
   - `SodPolicyRepository` — queries: `findByDirectoryId`, `findByEnabledTrue`, `findByDirectoryIdAndEnabledTrue`
   - `SodViolationRepository` — queries: `findByPolicyId`, `findByStatus`, `countByStatus`, `findByDirectoryIdAndStatus` (via join)

4. **DTOs**
   - `CreateSodPolicyRequest` / `UpdateSodPolicyRequest`
   - `SodPolicyResponse` (includes current violation count)
   - `SodViolationResponse`
   - `SodScanResultDto` (summary of a scan run)

5. **Service: `SodPolicyService.java`**
   - CRUD for policies
   - `scanDirectory(directoryId)` — for each enabled policy on that directory, fetch members of group A and group B from LDAP (via `LdapGroupService`), compute set intersection, create/update violations
   - `checkMembership(directoryId, userDn, groupDn)` — called during group assignment to check if adding this user to this group would violate any policy where the other group already contains the user. Returns list of violations. If any policy has `action=BLOCK`, throw `SodViolationException`
   - `exemptViolation(violationId, reason, principal)` — mark a violation as exempted
   - `resolveViolation(violationId)` — mark resolved (called when membership is removed)

6. **Controller: `SodPolicyController.java`**
   - `POST /api/v1/directories/{dirId}/sod-policies` — create policy
   - `GET /api/v1/directories/{dirId}/sod-policies` — list policies
   - `GET /api/v1/directories/{dirId}/sod-policies/{id}` — get policy with violations
   - `PUT /api/v1/directories/{dirId}/sod-policies/{id}` — update policy
   - `DELETE /api/v1/directories/{dirId}/sod-policies/{id}` — delete policy
   - `POST /api/v1/directories/{dirId}/sod-policies/scan` — trigger full scan
   - `GET /api/v1/directories/{dirId}/sod-violations` — list all violations (filterable by status)
   - `POST /api/v1/directories/{dirId}/sod-violations/{id}/exempt` — exempt a violation

7. **Integration with group assignment**
   - Hook into the existing group membership modification flow (in `LdapGroupService` or the controller that calls it) to call `SodPolicyService.checkMembership()` before adding a user to a group
   - If `action=BLOCK`, reject with 409 Conflict and include the conflicting policy details
   - If `action=ALERT`, allow but create the violation record and log an audit event

8. **Audit integration**
   - Log SoD events via `AuditService`: policy created/updated/deleted, scan executed, violation detected, violation exempted

#### Frontend

9. **API layer: `frontend/src/api/sodPolicies.js`**
   - Functions for all CRUD and scan endpoints

10. **Pinia store: `frontend/src/stores/sodPolicies.js`**
    - State for policies list, current policy, violations, loading states

11. **Views**
    - `SodPoliciesView.vue` — list policies with violation counts, enable/disable toggle, scan button
    - `SodPolicyFormView.vue` — create/edit form with group picker (reuse existing group browser from campaign creation), severity and action dropdowns
    - `SodViolationsView.vue` — table of violations with filters (status, severity), exempt action with reason dialog

12. **Router** — add routes under `/directories/:id/sod-policies`

---

## Feature 2.2: Compliance Posture Dashboard

**Effort:** 1 week | **Priority:** High | **Dependencies:** SoD engine (2.1) for violation count widget

### What Exists
- `DashboardService.java` returns basic stats: user/group counts per directory, pending approvals, active campaigns, recent audit events
- `DashboardController.java` exposes `GET /api/v1/superadmin/dashboard`
- `DashboardView.vue` renders the stats as cards

### What to Build

#### Backend

1. **Extend `DashboardService.getDashboard()`** to include:
   - **Campaign completion %** — for each active campaign, calculate `(decided / total members) * 100`. Query via `AccessReviewDecisionRepository`
   - **Pending approval aging** — group pending approvals by age buckets: <24h, 1-3 days, 3-7 days, 7+ days. Use `PendingApprovalRepository` with `createdAt` field
   - **SoD violation count** — `SodViolationRepository.countByStatus(OPEN)` (available after 2.1)
   - **Accounts not reviewed in 90+ days** — query campaigns completed >90 days ago with no newer campaign covering the same groups. Or simpler: count of directory users whose DN does not appear in any `AccessReviewDecision` with `decidedAt` in the last 90 days
   - **Overdue campaigns** — campaigns past deadline but still active

2. **New DTO: `ComplianceDashboardDto`** with structured sections rather than raw Map

#### Frontend

3. **Redesign `DashboardView.vue`**
   - Top row: summary stat cards (open SoD violations, campaign completion %, pending approvals, overdue campaigns)
   - Middle row: approval aging chart (horizontal bar chart or table), campaign progress bars
   - Bottom row: recent audit events (keep existing)
   - Use color coding: green (healthy), yellow (warning), red (critical) for thresholds

4. **Add drill-down links** — clicking a stat card navigates to the relevant detail view (e.g., clicking SoD violations goes to violations list)

---

## Feature 2.3: Scheduled Access Reviews with Auto-Reminders

**Effort:** 1.5 weeks | **Priority:** High | **Dependencies:** None

### What Exists
- `AccessReviewCampaign` entity has `recurrenceMonths`, `deadlineDays`, `sourceCampaignId`, `autoRevoke`, `autoRevokeOnExpiry`
- `AccessReviewScheduler` runs daily at 2 AM: expires overdue campaigns, creates recurring follow-ups, sends deadline reminders (configurable days before)
- `AccessReviewNotificationService` sends emails for: reviewer assignment, deadline approaching, campaign closure, campaign expiry
- Frontend campaign creation form supports `recurrenceMonths` input
- V23 migration added the recurrence columns

### What to Build

#### Backend

1. **Escalation workflow in `AccessReviewScheduler`**
   - Add a second scheduled pass (or extend `processDeadlines`) for escalation:
     - 7 days past assignment with no decisions → send reminder to reviewer (already exists via `reminderDays`)
     - 14 days past assignment with no decisions → escalate to campaign creator / directory admin
   - Add configurable properties: `ldapadmin.access-review.escalation-days` (default: 14)

2. **Database migration `V42__campaign_reminder_tracking.sql`**
   - `campaign_reminders` table: `id`, `campaign_id (FK)`, `reviewer_account_id (FK)`, `reminder_type (ENUM: DEADLINE, ESCALATION)`, `sent_at`
   - Prevents duplicate reminders — scheduler checks if reminder already sent before sending

3. **Auto-revoke execution**
   - In `AccessReviewScheduler`, when a campaign expires with `autoRevokeOnExpiry=true`:
     - Find all undecided memberships
     - For each, create an `AccessReviewDecision` with decision `REVOKE` and actor "SYSTEM"
     - Call `LdapGroupService` to remove the member from the group
     - Log audit events for each auto-revocation
   - Add a safety config: `ldapadmin.access-review.auto-revoke-enabled` (default: false) — global kill switch

4. **Extend `AccessReviewNotificationService`**
   - `notifyEscalation(campaign, reviewer, escalationTarget)` — email the campaign creator that reviewer X has not responded
   - Include summary of pending decisions in escalation email

#### Frontend

5. **Campaign creation form enhancements**
   - Add "Auto-revoke on expiry" toggle with warning text
   - Add escalation settings section: escalation contact, escalation days
   - Improve recurrence UI: show next scheduled run date based on recurrenceMonths

6. **Campaign detail view**
   - Show reminder/escalation history (from `campaign_reminders` table)
   - Show per-reviewer progress: decided vs. pending count
   - Visual indicator for reviewers who have been escalated

---

## Feature 2.4: Evidence Package Export

**Effort:** 1 week | **Priority:** High | **Dependencies:** SoD engine (2.1) for SoD policy export

### What Exists
- `PdfReportService` generates three PDF reports (User Access, Access Review Summary, Privileged Account Inventory) using OpenPDF
- `ComplianceReportController` exposes PDF download endpoints
- CSV export for campaign decisions exists
- `AccessReviewCampaignHistory` entity tracks campaign state changes

### What to Build

#### Backend

1. **New service: `EvidencePackageService.java`**
   - `generateEvidencePackage(directoryId, campaignIds, options)` → `byte[]` (ZIP)
   - Package contents:
     - `manifest.json` — package metadata: generated at, generated by, directory info, list of included files, SHA-256 checksums of each file
     - `reports/user-access-report.pdf` — from existing `PdfReportService`
     - `reports/privileged-account-inventory.pdf` — from existing `PdfReportService`
     - For each campaign:
       - `campaigns/{name}/access-review-summary.pdf` — existing report
       - `campaigns/{name}/decisions.csv` — existing CSV export
       - `campaigns/{name}/history.json` — campaign state change history from `AccessReviewCampaignHistory`
     - `sod/policies.json` — all SoD policy definitions (after 2.1)
     - `sod/violations.json` — current open violations
     - `approval-history/approvals.json` — approval workflow records from `PendingApproval` entity
     - `entitlements/user-entitlements.json` — snapshot of all users and their group memberships from LDAP

2. **Timestamping**
   - Include generation timestamp in manifest
   - Include SHA-256 checksums for each file in the package
   - Sign the manifest with HMAC-SHA256 using the `ENCRYPTION_KEY` — provides tamper evidence without requiring PKI

3. **Controller endpoint**
   - `POST /api/v1/directories/{dirId}/evidence-package` — request body specifies which campaigns to include, options (include SoD, include entitlements snapshot)
   - Returns `application/zip` with `Content-Disposition: attachment; filename="evidence-package-{date}.zip"`
   - Rate limit: one concurrent generation per user (it's resource-intensive)

4. **DTO: `EvidencePackageRequest`** — campaignIds list, boolean flags for optional sections

#### Frontend

5. **Evidence Package UI in `ComplianceReportsView.vue`**
   - New "Evidence Package" section
   - Campaign multi-select (checkboxes)
   - Toggle options: include SoD policies, include entitlement snapshot
   - Download button with loading spinner (generation may take 10-30 seconds)
   - Success toast with file size

---

## Feature 2.5: Campaign Templates

**Effort:** 3-4 days | **Priority:** Medium | **Dependencies:** None

### What Exists
- Campaign creation flow: `CreateCampaignRequest` DTO → `AccessReviewCampaignService.createCampaign()` → `AccessReviewCampaign` entity
- Campaign includes: name, description, directory, groups (with reviewer assignments), deadlineDays, recurrenceMonths, autoRevoke, autoRevokeOnExpiry

### What to Build

#### Backend

1. **Database migration `V43__campaign_templates.sql`**
   - `campaign_templates` table: `id (UUID)`, `directory_id (FK)`, `name`, `description`, `config (JSONB)`, `created_by (FK)`, `created_at`, `updated_at`
   - `config` JSONB stores: group DNs with reviewer assignments, deadlineDays, recurrenceMonths, autoRevoke, autoRevokeOnExpiry

2. **Entity: `CampaignTemplate.java`**
   - Standard JPA entity with `@JdbcTypeCode(SqlTypes.JSON)` for the config column

3. **DTO**
   - `CampaignTemplateResponse` — id, name, description, directoryId, config, createdAt
   - `CreateCampaignTemplateRequest` — name, description, config
   - `CampaignTemplateConfigDto` — structured config: groups[], deadlineDays, recurrenceMonths, autoRevoke, autoRevokeOnExpiry

4. **Service: `CampaignTemplateService.java`**
   - CRUD operations
   - `createFromCampaign(campaignId)` — extract config from an existing campaign and save as template
   - `applyCampaignTemplate(templateId, overrides)` — create a new campaign from a template, with optional overrides for name, description, dates

5. **Controller: `CampaignTemplateController.java`**
   - `POST /api/v1/directories/{dirId}/campaign-templates` — create template
   - `GET /api/v1/directories/{dirId}/campaign-templates` — list templates
   - `GET /api/v1/directories/{dirId}/campaign-templates/{id}` — get template
   - `PUT /api/v1/directories/{dirId}/campaign-templates/{id}` — update template
   - `DELETE /api/v1/directories/{dirId}/campaign-templates/{id}` — delete template
   - `POST /api/v1/directories/{dirId}/campaign-templates/{id}/create-campaign` — create campaign from template
   - `POST /api/v1/directories/{dirId}/campaigns/{campaignId}/save-as-template` — save existing campaign as template

#### Frontend

6. **API layer: `frontend/src/api/campaignTemplates.js`**

7. **Views**
   - `CampaignTemplatesView.vue` — list templates with "Create Campaign" action button on each
   - Template form (inline or modal) for creating/editing templates
   - "Save as Template" button on campaign detail view

8. **Campaign creation form**
   - Add "Start from template" dropdown at the top of the form
   - Selecting a template pre-fills all fields; user can override before submitting

---

## Implementation Order

The recommended build sequence optimizes for dependencies and incremental value:

```
Week 5:     2.5 Campaign Templates (3-4 days) — quick win, no dependencies
            2.3 Scheduled Reviews — start (escalation + reminder tracking)

Week 6:     2.3 Scheduled Reviews — finish (auto-revoke execution)
            2.1 SoD Policy Engine — start (DB, entities, service, scan logic)

Week 7:     2.1 SoD Policy Engine — continue (controller, block-on-assign, frontend)

Week 8:     2.1 SoD Policy Engine — finish (frontend views, testing)
            2.2 Compliance Dashboard — start

Week 9:     2.2 Compliance Dashboard — finish
            2.4 Evidence Package Export — start

Week 10:    2.4 Evidence Package Export — finish
            Integration testing across all Phase 2 features
            Update admin quick-start guide with Phase 2 documentation
```

### Migration Numbers Reserved
- V40: `sod_policies` and `sod_violations` tables
- V41: SoD feature permissions
- V42: `campaign_reminders` table
- V43: `campaign_templates` table

---

## Testing Strategy

- **Unit tests** for each new service (SodPolicyService, EvidencePackageService, CampaignTemplateService)
- **Integration tests** for SoD block-on-assign flow (mock LDAP, verify 409 response)
- **Scheduler tests** for escalation and auto-revoke logic (use `@SpyBean` on notification service)
- **PDF/ZIP tests** for evidence package (verify ZIP structure, manifest checksums)
- **Frontend** — manual testing until a test runner is configured

## Exit Criteria

- [ ] SoD policies can be defined, scanned, and enforced (alert or block) on group assignment
- [ ] Compliance dashboard shows: campaign completion %, approval aging, SoD violations, overdue campaigns
- [ ] Campaigns auto-recur on schedule with escalation emails at 14 days
- [ ] Auto-revoke executes on campaign expiry when configured
- [ ] One-click evidence package ZIP includes all reports, decisions, SoD data, and entitlements
- [ ] Campaign templates save and restore full campaign configurations
- [ ] All new backend code has unit test coverage
