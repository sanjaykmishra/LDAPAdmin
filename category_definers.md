# Category-Defining Features — LDAPAdmin Roadmap

## Current Strengths

LDAPAdmin already has a genuinely complete feature set — SoD, access drift, access reviews, compliance reports, integrity checks, playbooks, HR integration, self-service portal, SIEM export. Most competitors have 2-3 of these. That's the moat.

---

## What's Missing to Win

### 1. Visual Identity & Polish

- **Dashboard with real data** — the superadmin dashboard should show at-a-glance metrics: total users/groups across directories, open SoD violations, drift findings, upcoming access review deadlines, recent audit events.
- **Data visualizations** — charts showing access drift trends over time, SoD violation counts by severity, user growth, group membership distribution. A line chart showing "drift findings over last 6 months" tells a story that a table never will.
- **Consistent empty states** — every empty state should guide the user to the next action ("No SoD policies configured. Create your first policy to detect separation of duties violations.").

### 2. Workflow & Automation

- **Remediation actions** — detect drift and SoD violations AND fix them from the same screen. Add "Remove from group" buttons on findings that execute the LDAP modification directly, with approval workflow integration.
- **Scheduled access reviews** — campaigns are manual. Add recurring campaigns (quarterly recertification) that auto-create from templates on a schedule.
- **Proactive notifications** — email alerts when drift findings are detected, SoD violations are found, access review deadlines are approaching, or passwords are about to expire. The SMTP infrastructure exists but nothing sends proactive alerts.
- **Playbook execution tracking** — playbooks exist but need run history, step completion status, and audit trail per execution.

### 3. Enterprise Readiness

- **Multi-tenancy** — one installation per customer currently. A managed multi-tenant mode would enable SaaS for smaller customers while keeping on-prem for enterprises.
- **Fine-grained RBAC** — SUPERADMIN and ADMIN with feature permissions is good, but enterprises want: "this admin can only manage users in ou=Sales" or "this reviewer can only see their direct reports." OU-scoped and manager-scoped permissions.
- **API keys** — only JWT cookie auth currently. Enterprise customers want to script bulk operations and integrate with their own tools. Add API key auth with scoped permissions.
- **SCIM provisioning** — enterprises want to push user lifecycle events from their HR system or IdP via SCIM. HR integration exists, but SCIM is the standard connector format.
- **Audit log API** — SIEM export exists but there's no way to query audit events via API for custom integrations.

### 4. Intelligence & Insights

- **Risk scoring** — aggregate drift findings, SoD violations, sensitive group memberships, and access anomalies into a per-user risk score. Lets admins prioritize remediation.
- **Peer comparison visualization** — access drift detects outliers but doesn't show "here's what your peers have vs. what you have." A visual diff would make findings actionable.
- **Dormant account detection** — detect accounts that haven't logged in for 90+ days while still having active group memberships. This is a top audit finding.
- **Privilege creep timeline** — show how a user's group memberships changed over time using snapshot history. "Jane was in 3 groups in January, 12 groups by June."

### 5. User Experience

- **Onboarding wizard per feature** — the setup wizard handles initial config, but each feature (SoD, drift, access reviews) should have its own first-time guidance.
- **Unified search** — a single search bar (Cmd+K pattern) that finds users, groups, policies, violations, and audit events.
- **Bulk actions on report results** — view users with no groups, select 10, add them all to a group from the results table.
- **Mobile/responsive layout** — the sidebar layout doesn't work on tablets. IT admins increasingly use iPads in meetings.

### 6. Competitive Differentiators

- **Policy-as-code** — let administrators define SoD rules, access policies, and drift thresholds as YAML/JSON files that can be version-controlled alongside infrastructure code. No competitor does this well.
- **Compliance framework mapping** — tag SoD policies and access reviews to specific compliance controls (SOX 404, ISO 27001 A.9, NIST AC-6). Compliance reports then show coverage per framework.
- **Directory comparison** — compare two snapshots or two directories side-by-side to see what changed. Useful for pre/post migration validation.
- **Slack/Teams integration** — send access review assignments, drift alerts, and approval requests to chat channels. Reduces time-to-action.

---

## Priority Order

The 5 things with the most impact:

| Priority | Feature | Why |
|----------|---------|-----|
| 1 | Dashboard with real metrics + charts | First impression defines perception |
| 2 | Remediation actions on findings | Detect AND fix, not just detect |
| 3 | Proactive email notifications | The app should reach out to users, not wait for them |
| 4 | Risk scoring per user | Aggregates everything into one actionable number |
| 5 | Dormant account detection | The #1 audit finding that every customer has |
