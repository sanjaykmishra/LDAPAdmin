# Feature Recommendations — Making LDAPAdmin a Joy to Use

## Implemented Features

### 1. Dashboard / Home Page
**Status: Not Yet Implemented**
Superadmins currently land on the directories list. A dashboard showing user/group counts per directory, pending approvals, active access review campaigns, recent audit events, and system health would give immediate situational awareness.

### 2. Password Policy Visualization
**Status: Not Yet Implemented**
When resetting a user's password or viewing their account, show the LDAP password policy status: days until expiration, lockout status, failed login attempts, grace logins remaining. This is the #1 helpdesk pain point in LDAP environments. (Note: the password-reset modal does include a client-side strength meter, but no server-side policy status is displayed.)

### 3. Saved Search Filters / Bookmarks
**Status: Implemented**
The LDAP Search view (`LdapSearchView.vue`) supports saving named searches to `localStorage`. Users can save the current base DN, scope, filter, attributes, and limit under a named entry, then quickly reload saved searches from a dropdown. A "Clear saved" button is also provided.

### 4. Entry Comparison / Diff View
**Status: Not Yet Implemented**
Side-by-side comparison of two LDAP entries — invaluable for troubleshooting "why does user A have access but user B doesn't?" scenarios. Highlight attribute differences.

### 5. Keyboard Shortcuts
**Status: Implemented**
Global keyboard shortcuts registered via `useKeyboardShortcuts` composable in `AppLayout.vue`: `/` to focus search, `n` for new user, `g u` for users, `g g` for groups, `g a` for audit log, `Esc` to close modals, `?` to show shortcuts help overlay. Shortcuts are disabled when an input is focused. Help overlay provided by `KeyboardShortcutsHelp.vue`.

---

## Not Yet Implemented

### Dashboard / Home Page
A landing page showing user/group counts per directory, pending approvals, active access review campaigns, recent audit events, and system health for immediate situational awareness.

### Password Policy Visualization
Display LDAP password policy status on user accounts: days until expiration, lockout status, failed login attempts, grace logins remaining. This is the #1 helpdesk pain point in LDAP environments.

### Entry Comparison / Diff View
Side-by-side comparison of two LDAP entries to troubleshoot "why does user A have access but user B doesn't?" scenarios. Highlight attribute differences.

### 6. User Lifecycle Playbooks
**Status: Not Yet Implemented**
Onboarding and offboarding templates: "When onboarding an employee, add to these 5 groups, set these attributes, notify these people." Offboarding: disable account, remove from all groups, move to archived OU — all in one click. (Note: user templates exist for defining attribute schemas during creation, but not multi-step lifecycle playbooks.)

### 7. Scheduled Reports with Email Delivery
**Status: Implemented**
Scheduled report jobs are fully supported (`ScheduledReportJobController`, `ReportJobsView.vue`). Admins can create scheduled jobs with configurable report types, lookback periods, and cron-like scheduling. Reports can also be run on-demand with CSV download. SMTP settings are configurable in the application settings for email delivery. Referential integrity checks (`IntegrityCheckView.vue`) exist as an on-demand tool but are not yet schedulable with automated alerting.

### 8. Webhooks / Event Notifications
**Status: Not Yet Implemented**
Fire webhooks on key events (user created, group membership changed, approval needed) so external systems (Slack, ServiceNow, SIEM) can integrate.

### 9. Approval Delegation & Escalation
**Status: Not Yet Implemented**
The approval workflow is implemented (`PendingApprovalsView.vue`, `ApprovalController`) with approve/reject actions, but delegation to another admin when out of office and auto-escalation after X days are not yet built.

---

## Polish & Delight

### 10. Dark Mode
**Status: Implemented**
Theme toggle (Light / Dark / System) in Settings view via `useTheme` composable. Theme preference persisted in `localStorage`. Dark mode applied via `data-theme="dark"` attribute on `<html>`, with CSS overrides in `main.css` for backgrounds, text, borders, inputs, and badges. System mode respects `prefers-color-scheme`.

### 11. Inline Attribute Editing
**Status: Partially Implemented**
The Directory Browser (`DirectoryBrowserView.vue`) has an `EditEntryForm` component that allows editing attributes of any entry in the DIT. The User List view supports editing via a modal form. However, true click-to-edit directly from list/table cells (without opening a separate form or modal) is not yet implemented.

### 12. Activity Timeline per Entry
**Status: Not Yet Implemented**
On a user or group detail page, show a timeline of all changes: "March 15 — added to VPN Users by admin@corp", "March 10 — password reset by helpdesk". The audit log infrastructure exists (per-directory audit log with filterable events in `AuditLogView.vue`, plus external audit data sources), but a per-entry timeline view is not yet built.

### 13. Bulk Actions from Search Results
**Status: Implemented**
The User List view (`UserListView.vue`) supports multi-select via checkboxes on the `DataTable` component. Selected users can have bulk attribute updates applied (set/add/remove operations on arbitrary attributes). The Bulk Import/Export view (`BulkView.vue`) additionally supports CSV-based bulk operations for both users and groups, with configurable column-mapping templates.

### 14. Client-Side Form Validation
**Status: Partially Implemented**
The `FormField` component supports `required` attributes and hint text. The password reset modal includes a real-time password strength meter (weak/fair/good/strong) with visual progress bar and confirmation matching. However, regex-pattern validation, DN format validation, and other advanced client-side rules are not yet implemented.

### 15. i18n / Multi-Language
**Status: Not Yet Implemented**
Enterprise deployments in non-English-speaking organizations need this. Vue i18n makes it relatively straightforward. No `vue-i18n` integration exists in the codebase.


## Quick Wins

| Feature | Status |
|---|---|
| **Toast notifications with undo** | Implemented — `NotificationToast.vue` with success/error/info types, auto-dismiss, dismiss button, and optional undo callback. Callers pass `{ onUndo: fn }` to `success()` for destructive actions. |
| **Recent items** | Not yet implemented — no quick-access list of recently viewed users/groups. |
| **Copy DN to clipboard** | Implemented — `CopyButton.vue` is used on user DNs in the list view and in the Directory Browser's edit form. One-click copy with visual feedback. |
| **Relative timestamps** | Implemented — `RelativeTime.vue` component and `useRelativeTime` composable using `Intl.RelativeTimeFormat`. Shows "2 hours ago" with absolute time in tooltip. Integrated into Audit Log, Campaign List, Campaign Detail, and Pending Approvals views. |
| **Empty state illustrations** | Implemented — `EmptyState.vue` component with SVG icon variants (search, users, folder, shield, clipboard). Integrated into `DataTable.vue` via `emptyIcon` prop; used across User, Audit, Campaign, and Approval views. |
| **Column sorting & persistence** | Not yet implemented — `DataTable` does not support clickable column sorting or persisted sort preferences. |

---

## Additional Implemented Features (Not in Original List)

These significant features have been built but were not part of the original recommendations:

- **Access Review Campaigns** — Full lifecycle for periodic access reviews: create campaigns scoped to groups, assign reviewers, collect approve/revoke decisions, with recurrence scheduling.
- **Approval Workflow** — Operations (user creation, group membership, user moves) can require approval before executing. Pending approvals are managed in a dedicated view.
- **Self-Service Portal** — End users can log in via a separate self-service flow to view/edit their profile, change their password, and manage group memberships.
- **Self-Registration** — Public registration with email verification and admin approval flow.
- **User Templates** — Define attribute schemas (required fields, object classes, RDN attribute) per template. Templates are linked to realms and drive the user creation form.
- **Realms** — Logical groupings within a directory (user base DN, group base DN, linked templates) enabling multi-tenant-like scoping.
- **Schema Browser** — Introspect LDAP schema: object classes, attribute types, and their properties.
- **Directory Browser** — Two-panel DIT browser with tree navigation, entry detail view, inline editing, LDIF import, and entry creation.
- **LDAP Search** — Advanced search with configurable base DN, scope, filter, attributes, and result limits. Includes search history and saved searches.
- **CSV Import/Export** — Bulk user and group operations via CSV with configurable column-mapping templates. Preview before import, dry-run mode.
- **Referential Integrity Checker** — On-demand checks for broken member references, orphaned entries, and empty groups.
- **Audit Log** — Per-directory audit log with date range and action type filtering, pagination. Supports external audit data sources.
- **Multi-Auth Support** — Local, LDAP bind, and OIDC authentication methods, configurable in application settings.
- **Branding & Theming** — Configurable app name, logo URL, and primary/secondary colours applied as CSS custom properties.
- **RBAC** — Superadmin and realm-scoped admin roles with feature-level permission checks.

---

## Recommended Next Priorities

The biggest bang for the buck would be **Dashboard**, **Password Policy Visualization**, and **Activity Timeline per Entry** — they address the most frequent admin workflows and leverage existing infrastructure (audit log, directory connections).

For quick wins, **column sorting in DataTable** and **recent items** would further polish the existing UI with minimal effort.
