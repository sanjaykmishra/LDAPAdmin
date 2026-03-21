# LDAPAdmin Feature Roadmap

## High Impact — Core Workflow Gaps

### 1. Password Policy Visualization & Enforcement
Show password policy rules (from the LDAP server's `pwdPolicy` or DS password policy) when resetting passwords. Display expiration dates, failed login counts, and lockout status on user detail views. Admins constantly deal with "my account is locked" tickets — surfacing this saves enormous time.

### 2. Delegated Self-Service Portal
A separate, simplified UI for end users (not admins) to reset their own password, update contact info, or view their group memberships. This is the #1 feature that separates "admin tool" from "platform." Could reuse the existing LDAP auth + user template system.

### 3. Group Membership Diff / Comparison
Compare membership between two groups, or compare a user's current groups against a "role model" user. Critical for access reviews and onboarding ("give this new hire the same access as Jane").

### 4. Saved LDAP Search Filters
Let admins save, name, and share frequently-used search filters (e.g., "disabled accounts in ou=contractors", "users with no email"). The schema browser is already there — this adds reusability.

### 5. Bulk Attribute Update
Select multiple users from search results and apply a batch attribute change (e.g., set `department=Engineering` on 50 users, or disable 30 accounts). Bulk import already exists — this is the in-place equivalent.

---

## High Impact — Governance & Compliance

### 6. Access Review / Recertification Campaigns
Create time-bound campaigns where a manager reviews group memberships and confirms or revokes access. Generates an audit trail. This is a major compliance requirement (SOX, SOC2, ISO 27001) and almost no LDAP tool does it well.

### 7. Scheduled Integrity Checks
On-demand integrity checking already exists — add the ability to schedule them (like report jobs) and email results. Catch orphaned references and empty groups before they cause problems.

### 8. Audit Log Export & Retention Policies
Export audit logs as CSV/PDF, configure retention periods, and auto-purge old events. Important for compliance and keeps the database from growing unbounded.

---

## Medium Impact — Operational Excellence

### 9. Entry Comparison / Diff View
Side-by-side comparison of two LDAP entries showing attribute differences. Useful for debugging ("why does this account work but that one doesn't?").

### 10. Attribute Value Validation Rules
Configure regex or format rules per attribute in user templates (e.g., email must match `*@company.com`, employee ID must be 6 digits). Catch errors before they hit LDAP.

### 11. Favorites / Bookmarked Entries
Let admins bookmark frequently accessed users, groups, or DNs for quick access. Simple but saves daily friction.

### 12. Dashboard with Key Metrics
A landing page showing: total users/groups, recently modified entries, pending report jobs, recent audit events, integrity issues. Gives admins situational awareness at a glance.

### 13. LDAP Connection Health Monitoring
Show connection pool stats, response times, and availability history. Alert (via the existing SMTP config) when a directory becomes unreachable.

---

## Medium Impact — Workflow Automation

### 14. Provisioning Workflows / Approval Chains
When an admin creates a user or adds a group member, optionally require approval from another admin before the change is committed to LDAP. Adds a lightweight workflow layer.

### 15. User Lifecycle Templates
Define onboarding/offboarding playbooks: "When onboarding, create user, add to these 5 groups, set these default attributes." One-click execution of multi-step processes.

### 16. Webhook / Event Notifications
Fire webhooks on specific events (user created, group membership changed, etc.) so external systems can react. Enables integration with ticketing, IAM, and HR systems.

### 17. CSV Export Templates
Mirror what's built for import — let admins save named export configurations (which attributes, which filter, which base DN) for repeatable exports.

---

## Lower Effort, Nice Polish

### 18. Dark Mode
Branding/theming infrastructure already exists — add a dark mode toggle.

### 19. Keyboard Shortcuts
Power users navigating between users/groups/search benefit from shortcuts (e.g., `/` to focus search, `n` for new entry).

### 20. Entry History Timeline
For a given DN, show a timeline of all audit events (created, modified, moved, group adds/removes). The audit data exists — this is a presentation layer on top of it.

### 21. Multi-Language / i18n Support
Internationalize the UI. Enterprise LDAP tools are used globally.

---

## Recommended Priority Order

1. **Bulk Attribute Update** — low effort (reuses existing patterns), high daily value
2. **Dashboard with Key Metrics** — first thing admins see, sets a professional tone
3. **Password Policy Visualization** — solves the #1 helpdesk ticket category
4. **Saved Search Filters** — small feature, huge workflow improvement
5. **Access Review Campaigns** — the enterprise compliance killer feature that competitors charge premium for
