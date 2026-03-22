# Feature Recommendations — Making LDAPAdmin a Joy to Use

## Implemented Features

### 1. Toast Notifications ✅
Global notification system using a Pinia store (`useNotificationStore`) and `NotificationToast.vue` component. Supports success, error, and info types with auto-dismiss (4s for success/info, 6s for error) and animated slide-in transitions. Used across Users, Groups, Bulk, Settings, and other views.

### 2. Copy DN to Clipboard ✅
Reusable `CopyButton.vue` component with copy icon that switches to a checkmark on success. Used in user lists, group lists, edit entry forms, and the directory browser. Includes fallback for older browsers via `execCommand('copy')`.

### 3. Saved Search Filters / Bookmarks ✅
In `LdapSearchView.vue`, users can save named LDAP search filters and reload them from a dropdown. Searches are persisted in browser localStorage (`ldap-saved-searches`). Includes a "Clear saved" option.

### 4. Search History / Recent Searches ✅
Recent LDAP searches stored in localStorage with quick-access buttons for previously used filters and a "Clear History" option. Located in `LdapSearchView.vue`.

### 5. Bulk Actions from Search Results ✅
`DataTable.vue` supports checkbox selection with "Select All" and indeterminate state. `UserListView.vue` provides a bulk attribute update modal supporting SET (replace), ADD, and DELETE operations on multiple selected users simultaneously.

### 6. Bulk Import/Export ✅
`BulkView.vue` provides CSV import with preview, CSV export for users and groups, template-driven import with object class and RDN attribute specification, conflict handling strategies, and result summaries showing Created/Updated/Skipped/Errors counts.

### 7. Client-Side Form Validation ✅
`FormField.vue` provides required field indicators (red asterisks), HTML5 validation attributes, focus ring styling, and hint text. `UserListView.vue` includes a password strength meter with color-coded levels and confirm-password matching validation.

### 9. Form Layout Designer ✅
`FormLayoutDesigner.vue` offers live form preview during design, drag-and-drop field reordering, column span control, section grouping with customizable names, required field indicators, and computed DN display.

### 10. Branding / Theming ✅
`SettingsView.vue` and `ApplicationSettings.java` support customizable application name, logo URL, and primary/secondary color selection via color picker. Settings are persisted in the database.

### 10. Column Sorting ✅
`DataTable.vue` supports sortable columns across user, group, and other list views.

---

## Not Yet Implemented

### Dashboard / Home Page
A landing page showing user/group counts per directory, pending approvals, active access review campaigns, recent audit events, and system health for immediate situational awareness.

### Password Policy Visualization
Display LDAP password policy status on user accounts: days until expiration, lockout status, failed login attempts, grace logins remaining. This is the #1 helpdesk pain point in LDAP environments.

### Entry Comparison / Diff View
Side-by-side comparison of two LDAP entries to troubleshoot "why does user A have access but user B doesn't?" scenarios. Highlight attribute differences.

### Keyboard Shortcuts
`/` to search, `n` for new user, `g u` for users, `g g` for groups, `Esc` to close modals. Power users in enterprise tools live on keyboard shortcuts.

### User Lifecycle Playbooks
Onboarding and offboarding templates: "When onboarding an employee, add to these 5 groups, set these attributes, notify these people." Offboarding: disable account, remove from all groups, move to archived OU — all in one click.

### Scheduled Integrity Checks with Alerts
Run integrity checks on a schedule (daily/weekly) and email results when issues are found. Proactive rather than reactive.

### Webhooks / Event Notifications
Fire webhooks on key events (user created, group membership changed, approval needed) so external systems (Slack, ServiceNow, SIEM) can integrate.

### Approval Delegation & Escalation
Allow approvers to delegate to another admin when they're out of office. Auto-escalate if no action is taken within X days.

### Dark Mode
A toggle in settings — especially appreciated by admins who live in the tool all day.

### Inline Attribute Editing
Click-to-edit on user/group attributes directly from the list or detail view instead of opening a full form. Feels much faster for quick fixes.

### i18n / Multi-Language
Enterprise deployments in non-English-speaking organizations need this. Vue i18n makes it relatively straightforward.

### Toast Notifications with Undo
Extend the existing toast system with a 5-second undo button for destructive actions (e.g., "User deleted — Undo").

### Activity Timeline per Entry
On a user or group detail page, show a timeline of all changes: "March 15 — added to VPN Users by admin@corp", "March 10 — password reset by helpdesk". Pulls from audit log data you already have.

### Relative Timestamps
"2 hours ago" instead of "2026-03-22T08:30:00Z"

### Empty State Illustrations
Friendly graphics instead of bare "No data found" text on empty tables and search results.

---

## Recommended Next Priorities

The biggest bang for the buck would be **Dashboard**, **Password Policy Visualization**, and **Keyboard Shortcuts** — they address the most frequent admin workflows and the saved search/bookmark feature is already in place.
