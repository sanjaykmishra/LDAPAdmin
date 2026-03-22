# Implementation Plan — JOY_FEATURES.md

This plan organizes the 15 unimplemented Joy features into four phases, ordered by impact, dependency, and effort. Each feature includes the backend and frontend work required.

---

## Phase 1 — Quick Wins (Low Effort, High Polish)

These features require minimal backend work and can ship fast.

### 1.1 Relative Timestamps
**Effort:** Small | **Files:** Frontend only

- Create a `useRelativeTime` composable wrapping `Intl.RelativeTimeFormat`
- Utility function: given a date, return "2 hours ago", "3 days ago", etc.
- Add a `<RelativeTime>` component that accepts a date prop and shows relative text with a tooltip showing the absolute timestamp
- Integrate into: `AuditLogView`, `CampaignDetailView`, `CampaignListView`, `PendingApprovalsView`, and any other view using `fmtDate()`/`fmtDateTime()`

### 1.2 Keyboard Shortcuts
**Effort:** Small | **Files:** Frontend only

- Create a `useKeyboardShortcuts` composable using `@vueuse/core`'s `useMagicKeys` or plain `keydown` listeners
- Register global shortcuts in `AppLayout.vue`:
  - `/` → focus search input
  - `n` → open create-entry modal
  - `g u` → navigate to users list
  - `g g` → navigate to groups list
  - `Esc` → close active modal
- Add a `?` shortcut to show a shortcuts help overlay/modal
- Guard shortcuts so they don't fire when a text input is focused

### 1.3 Empty State Illustrations
**Effort:** Small | **Files:** Frontend only

- Create an `EmptyState.vue` component accepting `icon`, `title`, `description`, and optional `action` (button) props
- Use simple SVG illustrations or icon compositions (no external asset dependencies)
- Replace bare "No data" text in: `DataTable.vue` (zero rows), `AuditLogView` (no events), search results (no matches), `CampaignListView` (no campaigns)

### 1.4 Toast Notifications with Undo
**Effort:** Small | **Files:** `NotificationToast.vue`, `notifications.js` store

- Extend `useNotificationStore` to accept an optional `onUndo` callback and `undoDuration` (default 5s)
- In `NotificationToast.vue`, render an "Undo" button when `onUndo` is present; clicking it calls the callback and dismisses the toast
- Apply to destructive actions: user delete, group membership removal, bulk operations
- Backend consideration: for true undo, either delay the actual delete (soft-delete with TTL) or re-create the entry. Start with soft-delete flag on the backend (`DELETE /api/users/{dn}?soft=true`) or simply remove the undo for operations that can't be reversed.

### 1.5 Dark Mode
**Effort:** Small-Medium | **Files:** Frontend CSS + `SettingsView`, `settings.js` store

- Add a `theme` field to `ApplicationSettings` (values: `light`, `dark`, `system`)
- Define CSS custom properties for all colors in `:root` (light) and `[data-theme="dark"]` (dark)
- Refactor existing hardcoded colors in component `<style>` blocks to use CSS variables
- Add a theme toggle in `SettingsView.vue` and persist preference via the settings API
- In `App.vue`, apply `data-theme` attribute to `<html>` based on store value; respect `prefers-color-scheme` when set to `system`

---

## Phase 2 — Core UX Features (Medium Effort, High Impact)

These deliver the most value to daily admin workflows.

### 2.1 Dashboard / Home Page
**Effort:** Medium | **Dependencies:** Existing directory, audit, access review APIs

**Backend:**
- New `DashboardController` with `GET /api/dashboard/summary`
- Aggregate data from existing services:
  - `DirectoryConnectionService` → directory count
  - `LdapUserService` / `LdapGroupService` → user/group counts per directory
  - `ApprovalWorkflowService` → pending approval count
  - `AccessReviewCampaignService` → active campaign count
  - `AuditService` → recent audit events (last 24h)
- Return a single `DashboardSummaryDto` to minimize round-trips

**Frontend:**
- New `DashboardView.vue` with card-based layout:
  - Stat cards: total users, total groups, pending approvals, active campaigns
  - Per-directory breakdown table
  - Recent audit events list (last 10)
  - Quick action buttons: create user, run integrity check, start campaign
- Update router: change home redirect from `/superadmin/directories` to `/dashboard`
- Sidebar navigation: add Dashboard link at the top

### 2.2 Password Policy Visualization
**Effort:** Medium | **Dependencies:** LDAP password policy overlay support

**Backend:**
- Extend `LdapUserService` to query password policy operational attributes:
  - `pwdChangedTime`, `pwdAccountLockedTime`, `pwdFailureTime`, `pwdGraceUseTime`, `pwdPolicySubentry`
- Read the referenced `pwdPolicy` entry to get `pwdMaxAge`, `pwdMaxFailure`, `pwdLockoutDuration`, `pwdGraceAuthNLimit`
- New DTO `PasswordPolicyStatusDto`: `daysUntilExpiry`, `isLocked`, `failedAttempts`, `graceLoginsRemaining`, `lockoutEndsAt`, `policyDn`
- New endpoint: `GET /api/directories/{dirId}/users/{dn}/password-policy`
- Also add `POST /api/directories/{dirId}/users/{dn}/unlock` to clear `pwdAccountLockedTime`

**Frontend:**
- New `PasswordPolicyCard.vue` component showing:
  - Expiry countdown with color-coded badge (green/yellow/red)
  - Lock status with unlock button
  - Failed attempt count
  - Grace logins remaining
- Integrate into `UserListView.vue` detail panel and password reset modal
- Add a "locked" badge/icon column in the user list table

### 2.3 Entry Comparison / Diff View
**Effort:** Medium | **Files:** New view + backend endpoint

**Backend:**
- New endpoint: `GET /api/directories/{dirId}/entries/compare?dn1=...&dn2=...`
- Fetch both entries via `LdapEntryMapper`, return both attribute maps in a single response
- Compute diff on the backend: attributes only in A, only in B, in both but different, in both and equal

**Frontend:**
- New `EntryCompareView.vue` accessible from directory browser or user/group lists
- Two `DnPicker` components to select entries to compare
- Side-by-side table: attribute name | Entry A value | Entry B value
- Color-code rows: green (match), yellow (different), red (missing from one side)
- Add "Compare" button to `DataTable.vue` selection toolbar (when exactly 2 rows selected)

### 2.4 Inline Attribute Editing
**Effort:** Medium | **Files:** Frontend component + existing LDAP modify API

**Frontend:**
- New `InlineEdit.vue` component: displays value as text, on click switches to input, saves on blur/Enter, cancels on Esc
- Integrate into `EditEntryForm.vue` and user/group detail views
- On save, call existing `PATCH /api/directories/{dirId}/entries/{dn}` with the single modified attribute
- Show toast notification on success
- Handle validation errors inline (red border + message)

### 2.5 Activity Timeline per Entry
**Effort:** Medium | **Dependencies:** Audit log infrastructure

**Backend:**
- New endpoint: `GET /api/directories/{dirId}/entries/{dn}/timeline`
- Query `AuditEvent` table filtered by target DN, ordered by timestamp desc
- Return list of `TimelineEventDto`: `timestamp`, `action`, `actor`, `details`, `changedAttributes`
- If audit data source is LDAP changelog, also query `LdapChangelogReader` for the entry's DN

**Frontend:**
- New `EntryTimeline.vue` component with vertical timeline layout
- Each event shows: icon (based on action type), relative timestamp, actor, description
- Collapsible attribute-level detail for modify operations
- Integrate as a tab or expandable section in user/group detail views

---

## Phase 3 — Workflow & Automation (Higher Effort)

These features add operational automation capabilities.

### 3.1 User Lifecycle Playbooks
**Effort:** Large | **Dependencies:** Provisioning profiles, group service, LDAP operations

**Backend:**
- New entity: `LifecyclePlaybook` with fields: `name`, `type` (ONBOARD/OFFBOARD), `directoryId`, `steps` (JSON array)
- Each step: `action` (ADD_TO_GROUP, SET_ATTRIBUTE, MOVE_OU, DISABLE, NOTIFY), `parameters` (map)
- New `LifecyclePlaybookService`:
  - `executePlaybook(playbookId, targetDn)` — runs steps in order, collects results
  - Wraps each step in audit logging
  - Returns execution report with per-step success/failure
- New `LifecyclePlaybookController`: CRUD endpoints + `POST .../execute`
- Integrate with existing `ApprovalWorkflowService` to optionally require approval before execution

**Frontend:**
- New `PlaybooksView.vue` for managing playbooks (CRUD)
- Playbook designer: ordered list of steps, each with action type dropdown and parameter fields
- "Run Playbook" button on user detail view with a confirmation modal showing what will happen
- Execution result modal showing step-by-step outcomes

### 3.2 Scheduled Integrity Checks with Alerts
**Effort:** Medium | **Dependencies:** Existing `IntegrityCheckService`, email/notification infrastructure

**Backend:**
- New entity: `ScheduledIntegrityJob` with fields: `cronExpression`, `directoryId`, `emailRecipients`, `enabled`
- Extend existing `IntegrityCheckService` (if it exists) or create one that runs checks:
  - Orphaned group members
  - Users without required attributes
  - Empty groups
  - Duplicate attribute values
- New `IntegrityScheduler` using Spring `@Scheduled` or Quartz:
  - Runs jobs per their cron schedule
  - On findings, sends email summary via `JavaMailSender`
  - Stores results in `IntegrityCheckResult` entity for history
- Controller endpoints for CRUD on scheduled jobs + manual trigger

**Frontend:**
- Extend `IntegrityCheckView.vue` with a "Schedule" tab
- Cron expression builder (predefined options: daily, weekly, monthly + custom)
- Email recipient list input
- History table showing past runs with status and finding count

### 3.3 Webhooks / Event Notifications
**Effort:** Medium-Large | **Dependencies:** Audit event system

**Backend:**
- New entity: `WebhookSubscription` with fields: `url`, `secret`, `events[]` (enum: USER_CREATED, GROUP_CHANGED, APPROVAL_NEEDED, etc.), `enabled`, `directoryId`
- New `WebhookService`:
  - `dispatch(event)` — matches event type against subscriptions, sends HTTP POST with JSON payload + HMAC signature header
  - Async dispatch via `@Async` or a message queue to avoid blocking the main operation
  - Retry with exponential backoff (3 attempts)
  - Log delivery status in `WebhookDeliveryLog` entity
- Hook into `AuditService` — after logging an audit event, dispatch matching webhooks
- Controller: CRUD for subscriptions + `POST .../test` to send a test payload
- Provide payload schema documentation

**Frontend:**
- New `WebhooksView.vue` for managing subscriptions
- Form: URL, secret (masked), event type multi-select, directory scope
- Delivery log table with status, response code, timestamp
- "Test" button to send a sample event

### 3.4 Approval Delegation & Escalation
**Effort:** Medium | **Dependencies:** Existing approval workflow

**Backend:**
- Extend `ProfileApprover` entity with: `delegateTo` (account reference), `delegateUntil` (date), `escalateAfterHours` (int)
- Extend `ApprovalWorkflowService`:
  - When resolving approver, check if delegation is active (current date < `delegateUntil`) and route to delegate
  - New `ApprovalEscalationScheduler`: runs hourly, finds pending approvals older than `escalateAfterHours`, escalates to next-level approver or superadmin
  - Send notification on delegation and escalation
- New endpoints on existing approval controller:
  - `POST /api/approvals/delegate` — set delegation
  - `DELETE /api/approvals/delegate` — revoke delegation

**Frontend:**
- In `PendingApprovalsView.vue`, add "Delegate" button opening a modal:
  - Select delegate (from admin list)
  - Set return date
- Show delegation status banner when viewing as a delegate
- Settings section for escalation timeout per profile

---

## Phase 4 — Enterprise Polish (Ongoing)

### 4.1 i18n / Multi-Language
**Effort:** Large (ongoing) | **Files:** All frontend views

- Install and configure `vue-i18n`
- Create locale files: `en.json` (extract all hardcoded strings first), then additional languages
- Replace all hardcoded strings in templates with `$t('key')` calls
- Add language selector in settings (persist in `ApplicationSettings`)
- Backend: return translatable error codes instead of English messages; let the frontend map them
- Priority languages based on deployment targets (start with EN, then add FR, DE, ES, JA as needed)

---

## Dependency Graph

```
Phase 1 (no dependencies, can parallelize all):
  1.1 Relative Timestamps
  1.2 Keyboard Shortcuts
  1.3 Empty State Illustrations
  1.4 Toast with Undo
  1.5 Dark Mode

Phase 2 (mostly independent, some shared infrastructure):
  2.1 Dashboard ──────────────────┐
  2.2 Password Policy             │
  2.3 Entry Comparison            ├── can parallelize
  2.4 Inline Attribute Editing    │
  2.5 Activity Timeline ──────────┘

Phase 3 (some dependencies on Phase 2 audit/notification work):
  3.1 Lifecycle Playbooks (benefits from 2.5 timeline)
  3.2 Scheduled Integrity (independent)
  3.3 Webhooks (benefits from audit infrastructure)
  3.4 Approval Delegation (independent)

Phase 4:
  4.1 i18n (should run last — touching all views is easier after other features are stable)
```

---

## Summary

| Phase | Features | Estimated Scope |
|-------|----------|-----------------|
| 1 | Relative Timestamps, Keyboard Shortcuts, Empty States, Toast Undo, Dark Mode | 5 features, frontend-heavy |
| 2 | Dashboard, Password Policy, Entry Compare, Inline Edit, Activity Timeline | 5 features, full-stack |
| 3 | Lifecycle Playbooks, Scheduled Integrity, Webhooks, Approval Delegation | 4 features, backend-heavy |
| 4 | i18n | 1 feature, frontend-wide refactor |
