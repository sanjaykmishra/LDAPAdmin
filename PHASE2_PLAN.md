# Phase 2 Implementation Plan

## Overview

Phase 2 covers the remaining unimplemented features from JOY_FEATURES.md organized into three tiers by impact and effort. Each feature lists the files to create or modify, the approach, and dependencies.

---

## Tier 1 — High Impact, Leverages Existing Infrastructure

### 1. Superadmin Dashboard

**Goal:** Replace the bare directories-list landing page with a dashboard showing user/group counts, pending approvals, active access reviews, and recent audit events.

**Backend:**
- **New endpoint** `GET /api/v1/superadmin/dashboard` in a new `DashboardController`
  - For each enabled directory, call `LdapOperationService.searchUsers` and `searchGroups` with `limit=0` or `sizeLimit=1` and count via a lightweight LDAP search (using `countEntries` or search with `typesOnly=true`)
  - Aggregate pending approval counts across directories (existing `ApprovalWorkflowService.countPending`)
  - Fetch active access review campaigns (existing `AccessReviewCampaignService.list` filtered to `ACTIVE` status)
  - Fetch last 10 audit events (existing `AuditQueryService.query` with `size=10`)
- **New DTO** `DashboardResponse` — `{ directories: [{ id, name, userCount, groupCount }], pendingApprovals: long, activeCampaigns: [...], recentAudit: [...] }`
- **Files:** New `DashboardController.java`, `DashboardService.java`, `DashboardResponse.java`

**Frontend:**
- **New view** `SuperadminDashboardView.vue` — stats cards at top (total users, groups, pending approvals), directory breakdown table, active campaigns summary, recent activity feed
- **New API** `dashboard.js` — `getDashboard()`
- **Router:** Change superadmin home route from `/superadmin/directories` to `/superadmin/dashboard`; add `/superadmin/dashboard` route
- **Files:** New `SuperadminDashboardView.vue`, `dashboard.js`; modify `router/index.js`

**Performance note:** User/group counts require LDAP searches. Use server-side caching (e.g. `@Cacheable` with 5-minute TTL) to avoid hitting LDAP on every page load. Counts only need to be approximate.

**Dependencies:** None — all underlying APIs and services already exist.

---

### 2. Activity Timeline per Entry

**Goal:** Show a chronological timeline of all changes to a specific user or group entry.

**Backend:**
- **Add `targetDn` filter** to `AuditLogController.get()` and propagate through `AuditQueryService` → `AuditEventRepository`
  - The `audit_events` table already has `target_dn` column with an index (`idx_audit_target_dn`)
  - Add `@RequestParam(required = false) String targetDn` and append `AND target_dn = :targetDn` to the native query
- **Files:** Modify `AuditLogController.java`, `AuditQueryService.java`, `AuditEventRepository.java`

**Frontend:**
- **New component** `EntryTimeline.vue` — vertical timeline with icons per action type, actor name, relative timestamp, and expandable detail (attribute changes from the JSONB `detail` field)
- **Integration point:** Add a "History" button/tab to the user edit modal in `UserListView.vue` and to the group views. When clicked, fetch `GET /api/v1/audit?targetDn=<dn>&directoryId=<dirId>&size=50`
- **New API function** in `audit.js`: `getEntryTimeline(dirId, targetDn, params)`
- **Files:** New `EntryTimeline.vue`; modify `UserListView.vue`, `audit.js`

**Dependencies:** None — audit infrastructure and indexes already exist.

---

### 3. Password Policy Visualization

**Goal:** Show LDAP password policy status on user entries: last changed, locked, expiring, grace logins remaining.

**Backend:**
- **New service** `PasswordPolicyService.java`:
  - Fetch operational attributes for a user DN: `pwdChangedTime`, `pwdAccountLockedTime`, `pwdFailureTime`, `pwdGraceUseTime`, `pwdPolicySubentry`
  - These require explicit attribute requests in LDAP search (operational attributes aren't returned by default) — use `SearchRequest` with `"+"` or named operational attrs
  - Parse timestamps (generalized time format) and compute derived state: `isLocked`, `isExpired`, `daysUntilExpiry`, `failedAttemptCount`, `graceLoginsRemaining`
  - Read the password policy entry itself (the `pwdPolicy` objectClass entry) to get `pwdMaxAge`, `pwdMaxFailure`, `pwdLockoutDuration`, `pwdGraceAuthNLimit`
- **New endpoint** `GET /api/v1/directories/{dirId}/users/password-status?dn=<userDn>` in `UserController`
- **New DTO** `PasswordPolicyStatusDto` — `{ lastChanged, isLocked, lockedSince, daysUntilExpiry, failedAttempts, graceLoginsRemaining, policyDn }`
- **Files:** New `PasswordPolicyService.java`, `PasswordPolicyStatusDto.java`; modify `UserController.java`

**Frontend:**
- **New component** `PasswordPolicyStatus.vue` — compact card/badge showing status with color coding (green = OK, yellow = expiring soon, red = locked/expired)
- **Integration:** Show in the user edit modal header area or as a collapsible section. Also show a warning icon in the user list for locked/expired accounts
- **New API function** in `users.js`: `getPasswordStatus(dirId, dn)`
- **Files:** New `PasswordPolicyStatus.vue`; modify `UserListView.vue`, `UserForm.vue`, `users.js`

**Caveats:** Only works with directories that have a password policy overlay (OpenLDAP ppolicy, 389DS, AD). If the policy attributes aren't present, the component should degrade gracefully (show "No password policy detected"). Consider adding a `passwordPolicyEnabled` flag to `DirectoryConnection` so admins can toggle visibility.

**Dependencies:** None — uses standard LDAP operational attributes.

---

## Tier 2 — Medium Impact, Moderate Effort

### 4. Entry Comparison / Diff View

**Goal:** Side-by-side comparison of two LDAP entries highlighting attribute differences.

**Backend:** No backend changes needed — use existing `browse(dirId, dn)` or `getUser(dirId, dn)` to fetch both entries.

**Frontend:**
- **New view** `EntryCompareView.vue`:
  - Two DN picker inputs (reuse `DnPicker` component)
  - Fetch both entries on submit
  - Compute attribute diff: added (in B not A), removed (in A not B), changed (different values)
  - Render side-by-side table with color-coded rows (green = added, red = removed, yellow = changed)
- **Router:** Add `/superadmin/compare` or `/directories/:dirId/compare` route
- **Files:** New `EntryCompareView.vue`; modify `router/index.js`, add nav link

**Dependencies:** None.

---

### 5. DataTable Column Sorting

**Goal:** Clickable column headers to sort table data, with sort direction indicators.

**Frontend only:**
- **Modify `DataTable.vue`:**
  - Add `sortable` prop to column definitions: `{ key, label, sortable: true }`
  - Add `sortBy` and `sortDir` refs (reactive state)
  - Wrap `rows` in a computed that sorts when `sortBy` is set
  - Render sort direction arrows (▲/▼) in `<th>` elements
  - Emit `sort` event for parents that want server-side sorting
- **Persist:** Store sort preference in `localStorage` keyed by a `tableId` prop
- **Files:** Modify `DataTable.vue`

**Dependencies:** None.

---

### 6. Recent Items

**Goal:** Quick-access list of recently viewed/edited users and groups.

**Frontend only:**
- **New composable** `useRecentItems.js`:
  - Store in `localStorage` under `recent-items` key
  - Track: `{ dn, label, type ('user'|'group'), directoryId, timestamp }`
  - Max 20 items, deduplicated by DN
  - Functions: `addRecent(item)`, `getRecents()`, `clearRecents()`
- **Record events:** Call `addRecent()` when opening user/group edit modals in `UserListView.vue` and `GroupListView.vue`
- **Display:** Add a "Recent" section to the sidebar in `AppLayout.vue` — collapsible list of recent items, clicking navigates to the directory and opens the entry
- **Files:** New `useRecentItems.js`; modify `AppLayout.vue`, `UserListView.vue`, `GroupListView.vue`

**Dependencies:** None.

---

### 7. Client-Side Form Validation

**Goal:** Real-time field validation with error messages in forms.

**Frontend only:**
- **Enhance `FormField.vue`:**
  - Add props: `rules` (array of validator functions), `errorMessage` (string ref)
  - Each rule: `(value) => true | 'Error message'`
  - Validate on blur and on form submit
  - Render error text below input: `<p class="text-xs text-red-500 mt-1">...</p>`
  - Add visual error state: red border ring
- **Built-in validators** in a `validators.js` utility:
  - `required(msg)`, `minLength(n)`, `maxLength(n)`, `pattern(regex, msg)`, `email()`, `dnFormat()`
- **Integration:** Apply rules to UserForm, profile creation, and admin forms
- **Files:** Modify `FormField.vue`; new `validators.js`; modify forms as needed

**Dependencies:** None.

---

## Tier 3 — High Effort, Architectural

### 8. User Lifecycle Playbooks

**Goal:** One-click onboarding/offboarding templates that execute multiple operations.

**Backend:**
- **New entity** `LifecyclePlaybook` — `{ id, name, type (ONBOARD|OFFBOARD), directoryId, steps: JSON }`
  - Steps: ordered list of actions — add to groups, set attributes, move entry, enable/disable, send notification
- **New service** `PlaybookExecutionService` — executes steps sequentially, with rollback on failure
- **New controller** `PlaybookController` — CRUD for playbooks + `POST /execute` endpoint
- **DB migration:** New `lifecycle_playbooks` table
- **Files:** New entity, repository, service, controller, DTO, migration

**Frontend:**
- **New view** `PlaybooksView.vue` — list playbooks, create/edit with step builder UI
- **New component** `PlaybookStepEditor.vue` — drag-and-drop step ordering, step type selection, parameter forms per step type
- **Integration:** Add "Run Playbook" button to user actions in `UserListView.vue`
- **Files:** New view, components, API file; modify router, nav

**Dependencies:** Profiles and group management must be stable.

---

### 9. Webhooks / Event Notifications

**Goal:** Fire HTTP webhooks on key events for external system integration.

**Backend:**
- **New entity** `WebhookEndpoint` — `{ id, url, secret, events: Set<AuditAction>, enabled, retryPolicy }`
- **New service** `WebhookDispatchService`:
  - Listen for audit events (extend `AuditService.record()` to also dispatch)
  - Async HTTP POST with HMAC signature in header
  - Retry with exponential backoff (3 attempts)
  - Store delivery attempts in `webhook_deliveries` table for debugging
- **New controller** `WebhookController` — CRUD + test endpoint + delivery log
- **DB migration:** `webhooks` and `webhook_deliveries` tables
- **Files:** New entity, repository, service, controller, DTOs, migration

**Frontend:**
- **New view** `WebhooksView.vue` — list/create/edit webhooks, event type multi-select, test button, delivery log viewer
- **Files:** New view, API file; modify router, nav

**Dependencies:** Audit system must be stable. Consider using Spring's `ApplicationEventPublisher` for loose coupling.

---

### 10. Approval Delegation & Escalation

**Goal:** Delegate approvals when out of office; auto-escalate after N days.

**Backend:**
- **New entity** `ApprovalDelegation` — `{ fromAccountId, toAccountId, startsAt, endsAt, active }`
- **Modify** `ApprovalWorkflowService`:
  - When checking approvers, also include delegates of assigned approvers
  - Add scheduled job to check for stale pending approvals and escalate (notify escalation contact or auto-approve/reject)
- **New endpoints** in `ApprovalController`: `POST /delegate`, `DELETE /delegate`, `GET /delegations`
- **DB migration:** `approval_delegations` table; add `escalation_days` and `escalation_account_id` columns to approval config
- **Files:** New entity, repository; modify service, controller, migration

**Frontend:**
- **Modify** `PendingApprovalsView.vue` — add delegation UI (delegate to dropdown, date range)
- **Modify** profile lifecycle tab — escalation settings are partially modeled already (`autoEscalateDays`, `escalationAccountId` in approval config)
- **Files:** Modify existing views and API files

**Dependencies:** Approval workflow must be stable.

---

### 11. i18n / Multi-Language

**Goal:** Full internationalization of the frontend.

**Frontend:**
- **Install** `vue-i18n`
- **Extract** all hardcoded strings from all `.vue` files into locale JSON files
- **Create** locale files: `en.json`, then add others as needed
- **Wrap** all text in `{{ $t('key') }}` calls
- **Add** language picker to Settings view
- **Files:** Every `.vue` file needs string extraction; new `i18n/` directory with locale files

**This is a sweeping change** — touching nearly every component. Best done as a dedicated pass after all other features are stable.

**Dependencies:** All other UI work should be complete first.

---

## Recommended Implementation Order

```
Sprint 1 (Quick Wins):
  ├── 5. DataTable column sorting
  └── 6. Recent items

Sprint 2 (High Impact):
  ├── 1. Dashboard
  └── 2. Activity timeline per entry

Sprint 3 (User-Facing Polish):
  ├── 3. Password policy visualization
  ├── 4. Entry comparison / diff
  └── 7. Client-side form validation

Sprint 4 (Architectural):
  ├── 8. Lifecycle playbooks
  └── 10. Approval delegation & escalation

Sprint 5 (Integration):
  └── 9. Webhooks / event notifications

Sprint 6 (Sweep):
  └── 11. i18n
```

Quick wins first to build momentum, then the three high-value features that the document specifically recommends (Dashboard, Password Policy, Activity Timeline), followed by architectural work that builds on a stable foundation.
