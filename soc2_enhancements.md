# SOC 2 Evidence Enhancements — Analysis & Implementation Plan

## Context

The auditor portal currently covers **CC6 (Logical Access)** thoroughly — access review campaigns, entitlements snapshots, SoD policies, approval workflows, and audit logs. These four enhancements extend coverage into adjacent SOC 2 criteria that auditors routinely request and that the platform is well-positioned to provide.

---

## 1. Password Policy Export (CC5.2 — Authentication & Credentials)

### What auditors ask for

"Show me the password policy enforced on directory accounts: minimum length, complexity requirements, lockout thresholds, expiry intervals, and history restrictions."

This is a standard CC5.2 evidence request. Auditors want to verify that the organization enforces strong authentication controls and that the policy hasn't been weakened during the audit period.

### What exists today

- The LDAP directory stores password policy in the directory itself (e.g., `cn=default,ou=pwpolicies` for OpenLDAP, or the Default Domain Policy GPO for Active Directory)
- The platform already connects to the directory via UnboundID SDK and can query arbitrary entries
- No current endpoint exposes password policy data

### What to build

**Backend:**
- `LdapPasswordPolicyService` — queries the directory's password policy entry:
  - OpenLDAP: reads `pwdPolicy` overlay attributes (`pwdMinLength`, `pwdMaxAge`, `pwdInHistory`, `pwdLockout`, `pwdLockoutDuration`, `pwdMaxFailure`, `pwdFailureCountInterval`)
  - Active Directory: reads `msDS-PasswordSettings` or the domain's `minPwdLength`, `maxPwdAge`, `pwdHistoryLength`, `lockoutThreshold`, `lockoutDuration` from the domain root
  - Returns a normalized `PasswordPolicyDto` regardless of directory type
- `AuditorPortalController` — new endpoint: `GET /api/v1/auditor/{token}/password-policy`
- Evidence package — new file: `security/password-policy.csv` with one row per policy attribute (Name, Value, Description)

**Frontend:**
- New section in the auditor portal sidebar: "Security Settings"
- Table showing each policy attribute with a human-readable description
- Green checkmarks for attributes meeting common benchmarks (e.g., min length ≥ 12, lockout ≤ 5 attempts)

**Scope:**
- Read-only — no ability to modify the policy through the portal
- One policy per directory (most directories have a single default policy)
- If the directory doesn't expose password policy (rare), show "Password policy not available from this directory type"

### Audit value

Eliminates the manual screenshot process. Auditors currently ask admins to take screenshots of the password policy GUI — this is error-prone, undated, and not independently verifiable. A live query with timestamp is stronger evidence.

---

## 2. Privileged Access Monitoring (CC6.1 — Logical Access Security)

### What auditors ask for

"Show me who has administrative access, what level of access they have, and evidence that privileged accounts are periodically reviewed."

CC6.1 requires that the organization restricts logical access to authorized users. For privileged accounts (admins, superadmins), auditors want to see:
1. A current inventory of all privileged accounts
2. What each account can do (role, feature permissions, directory scope)
3. When each account was last reviewed or modified
4. Whether any inactive privileged accounts exist

### What exists today

- The platform already has a full admin permission model: accounts, roles (SUPERADMIN, ADMIN), profile roles (per-directory base role + feature overrides)
- The `PdfReportService.generatePrivilegedAccountInventory()` produces a PDF listing all admin accounts
- The evidence package includes this PDF
- No current view shows privileged access in the auditor portal
- No cross-reference between privileged accounts and their last login or review date

### What to build

**Backend:**
- `AuditorPortalController` — new endpoint: `GET /api/v1/auditor/{token}/privileged-accounts`
  - Returns all SUPERADMIN and ADMIN accounts with:
    - Username, display name, role, auth type (LOCAL/LDAP/OIDC)
    - Active/inactive status
    - Last login timestamp
    - Profile roles (which directories, what base role)
    - Feature permission overrides
    - Account creation date
    - Days since last login (computed)
  - Flags accounts that haven't logged in for 90+ days as "potentially stale"
- Evidence package — new file: `security/privileged-accounts.csv`

**Frontend:**
- New section in the auditor portal: "Privileged Accounts" (under the proposed "Security Settings" section)
- Table with sortable columns: username, role, last login, status, profile access
- Visual indicators:
  - Red badge on accounts inactive 90+ days
  - Amber badge on accounts inactive 30-89 days
  - Tooltip showing full profile role details

**Scope:**
- Read-only view of the existing permission model
- Does not include self-service or LDAP end-user accounts — only platform admin accounts
- The privileged account inventory PDF already exists in the evidence package — this adds a browsable, filterable view in the portal

### Audit value

Provides a live, queryable privileged access inventory instead of a static PDF. Auditors can sort by last login to immediately spot stale accounts, filter by role to see superadmin vs. admin distribution, and verify that the principle of least privilege is applied.

---

## 3. Terminated User Evidence (CC6.6 — Access Removal on Termination)

### What auditors ask for

"Show me that when employees are terminated, their directory access is removed promptly. Provide evidence of the termination date from HR and the access removal date from the directory for each terminated employee during the audit period."

CC6.6 is one of the most scrutinized controls. Auditors want to see:
1. A list of all terminations during the audit period (from HR)
2. For each terminated employee, the timestamp when their directory account was disabled/deleted
3. The delta (business days between termination and access removal)
4. Evidence that the delta is within the organization's SLA (typically ≤ 1 business day)

### What exists today

- The HR integration module syncs employee data from BambooHR and detects orphaned accounts (LDAP accounts with no matching HR record)
- The audit log records `USER_DISABLE` and `USER_DELETE` events with timestamps
- The `TERMINATION_VELOCITY` compliance report already computes the termination-to-removal delta
- No current endpoint exposes this cross-referenced data to the auditor portal

### What to build

**Backend:**
- `AuditorPortalController` — new endpoint: `GET /api/v1/auditor/{token}/terminations`
  - Queries HR sync data for employees with a termination date within the evidence window (`dataFrom` to `dataTo`)
  - For each terminated employee, finds the corresponding `USER_DISABLE` or `USER_DELETE` audit event
  - Returns:
    - Employee name, HR employee ID
    - Termination date (from HR)
    - Account disable/delete date (from audit log)
    - Delta in business days
    - SLA status (Within SLA / Overdue / Pending)
    - Whether the account is currently disabled
  - Requires HR integration to be configured for the directory
- Evidence package — new file: `compliance/termination-velocity.csv`
- `AuditorExportController` — new endpoint: `GET /api/v1/auditor/{token}/export/terminations/csv`

**Frontend:**
- New section in the auditor portal: "Termination Evidence"
- Table: employee name, termination date, removal date, delta, SLA status
- Color-coded SLA status:
  - Green: within SLA (≤ 1 day)
  - Red: overdue
  - Amber: pending (terminated but not yet removed)
- Summary card at top: total terminations, % within SLA, average delta
- Only shown if the link's directory has HR integration configured

**Prerequisites:**
- HR integration must be connected for the link's directory
- The scope picker on the admin "New Auditor Link" dialog should add a new checkbox: "Include termination evidence"
- If HR integration is not configured, the section shows "HR integration not configured — termination evidence unavailable"

### Audit value

This is the single most impactful enhancement for SOC 2 audits. The termination-to-removal delta is a common audit finding. Most organizations collect this evidence manually by exporting HR termination lists and cross-referencing with AD disable timestamps in spreadsheets. Automating this eliminates weeks of evidence collection work and provides real-time, verifiable data.

---

## 4. Change Management Summary (CC8.1 — Change Management Process)

### What auditors ask for

"Show me a summary of all changes to the directory during the audit period, categorized by type, and evidence that changes followed an authorized process."

CC8.1 requires that changes to the IT environment are authorized, tested, and documented. For a directory service, this means:
1. An aggregate view of what changed (user creates, group modifications, password resets, etc.)
2. Volume trends showing change activity is consistent and controlled (no suspicious spikes)
3. Evidence that changes went through the approval workflow (for organizations with approvals enabled)
4. A breakdown of who made changes (which admins, how many)

### What exists today

- The audit log captures every directory change with actor, action, target, and timestamp
- The approval workflow tracks which changes were approved, by whom, and when
- The evidence package includes raw audit events
- No current view aggregates this data into a management-level summary

### What to build

**Backend:**
- `AuditorPortalController` — new endpoint: `GET /api/v1/auditor/{token}/change-summary`
  - Aggregates audit events within the evidence window:
    - **By action type**: count of USER_CREATE, USER_UPDATE, USER_DELETE, GROUP_MEMBER_ADD, GROUP_MEMBER_REMOVE, PASSWORD_RESET, etc.
    - **By month**: monthly volume for each action type (for trend analysis)
    - **By actor**: count of changes per admin user
    - **Approval coverage**: % of changes that went through the approval workflow vs. direct changes
  - Returns a structured summary, not raw events
- Evidence package — new file: `compliance/change-summary.csv` (one row per action type per month)

**Frontend:**
- New section in the auditor portal: "Change Management"
- Dashboard-style layout:
  - **Action breakdown**: horizontal bar chart showing count per action type
  - **Monthly trend**: line chart showing total changes per month across the evidence window
  - **Top actors**: table of admins ranked by change count
  - **Approval coverage**: donut chart — approved vs. direct changes
- Sortable detail table: month, action type, count, approved count, direct count

**Scope:**
- Uses only existing audit event data — no new data collection needed
- Scoped to the evidence window (`dataFrom`/`dataTo`) on the auditor link
- Approval coverage is only meaningful if the organization has the approval workflow enabled — if not, show "Approval workflow not enabled" instead of 0%

### Audit value

Transforms raw audit logs into an executive-level change management summary. Auditors currently export thousands of audit events and manually categorize them in spreadsheets to assess whether change management controls are operating effectively. This aggregation does that work automatically and presents it in a format auditors can screenshot for workpapers.

---

## Implementation Priority

| Enhancement | SOC 2 Criteria | Effort | Audit Impact | Prerequisites |
|---|---|---|---|---|
| **Terminated user evidence** | CC6.6 | Medium | **Very high** — most common finding | HR integration configured |
| **Privileged access monitoring** | CC6.1 | Low | **High** — always requested | None (uses existing data) |
| **Change management summary** | CC8.1 | Medium | **High** — turns raw logs into narrative | None (uses existing audit events) |
| **Password policy export** | CC5.2 | Medium | **Medium** — one-time per audit | LDAP schema supports policy query |

Recommended order: privileged access (quick win, no prerequisites) → change management summary (high value, uses existing data) → terminated user evidence (highest impact but requires HR integration) → password policy (useful but less frequently a finding).

---

## Estimated Scope Per Enhancement

Each enhancement follows the same pattern established by the existing portal:

1. **Backend service method** — query + aggregate data
2. **Backend controller endpoint** — under `/api/v1/auditor/{token}/...`
3. **Evidence package CSV** — added to the ZIP
4. **Frontend view** — new section in the portal sidebar
5. **Export endpoint** — CSV/PDF download per section

Total estimate: ~2-3 days per enhancement, including tests. All four could ship in a 2-week sprint.
