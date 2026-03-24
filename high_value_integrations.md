# High-Value Integrations Beyond Entra ID

## The Candidates

### 1. HR System (BambooHR / Workday / Personio) — Read-Only

**What it does:** Treats the HR system as the authoritative identity source. When someone is hired, transferred, or terminated in HR, LDAPAdmin knows.

**Why it matters more than any other integration:**

Every compliance framework (SOX, HIPAA, SOC2, ISO 27001) asks the same question: "When someone leaves the organization, how quickly is their access removed?" Right now, LDAPAdmin has no way to answer that. It can review access, but it doesn't know who should have access in the first place.

With an HR feed:

- **Joiner:** HR creates employee -> LDAPAdmin auto-provisions LDAP account via provisioning profile
- **Mover:** HR changes department -> LDAPAdmin flags stale group memberships for review
- **Leaver:** HR terminates employee -> LDAPAdmin disables account and triggers access review for all group memberships

This is the single most-asked-for capability in every IGA sales cycle. The access review feature answers "is current access appropriate?" The HR integration answers "should this person have access at all?" — which is the harder, more valuable question.

**Implementation scope:** Read-only polling or webhook. BambooHR has a simple REST API. Workday has a report-as-a-service pattern. You need:

- Employee list sync (name, email, department, status, manager)
- Status change detection (active -> terminated)
- Department/role change detection
- Map employee -> LDAP DN (by email or employee ID attribute)

**Market fit:** BambooHR dominates the 100-2,000 employee segment — exactly the mid-size LDAP shop. Workday covers 2,000+. Personio covers EU mid-market. Supporting even one of these covers a large chunk of the target.

---

### 2. Google Workspace — Read-Only

**What it does:** Imports Google Workspace users and group memberships into access reviews alongside LDAP.

**Why it's valuable:** Many mid-size orgs run LDAP for on-prem apps and Google Workspace for email/docs/drive. The compliance question is never "show me LDAP access" — it's "show me ALL access." Google Workspace groups control access to shared drives, Google Cloud projects, and third-party SaaS (via SAML/SSO). Reviewing LDAP groups without reviewing Google groups leaves a blind spot auditors will find.

**Implementation scope:** Google Admin SDK Directory API. Read-only. List users, list groups, list group members. Well-documented, straightforward OAuth2 service account auth.

**Why it ranks below HR:** It's another access source to review, but it doesn't solve the lifecycle problem. You can review Google groups without knowing who's been terminated.

---

### 3. SIEM / Syslog Export

**What it does:** Forwards audit events to Splunk, Elastic, or any syslog/CEF/LEEF receiver.

**Why it's valuable:** This isn't about adding governance features — it's about fitting into the customer's existing security stack. Every mid-size org with compliance requirements already has a SIEM. Auditors want a single pane of glass. If LDAPAdmin's audit trail lives only in its own database, the security team has to check two places. That's friction that makes them not use the tool.

**Implementation scope:** Minimal. LDAPAdmin already has a robust `AuditService` recording events asynchronously. Add:

- Syslog output (RFC 5424) over TCP/UDP/TLS
- CEF format (for Splunk/ArcSight)
- Optional webhook (for modern SIEMs like Panther, Sumo Logic)

This is probably 2-3 days of engineering. The ROI per engineering hour is the highest of any integration on this list.

**Why it ranks below HR and Google:** It doesn't expand what LDAPAdmin can govern. It just makes the existing governance more consumable.

---

### 4. ServiceNow / Jira — Ticketing Integration

**What it does:** Creates tickets for approval requests and access review tasks instead of (or in addition to) email notifications.

**Why it's valuable:** Mid-size orgs with compliance programs almost always have a ticketing system. Approval workflows via email get lost. Ticketing provides:

- Audit trail the auditor already trusts
- SLA tracking (how long did the approval take?)
- Escalation paths the org already has configured
- Evidence that reviewers were notified (ticket assignment timestamp)

**Implementation scope:** ServiceNow REST API or Jira REST API. Create ticket on approval request, update ticket on decision, close ticket on completion. LDAPAdmin already has `ApprovalNotificationService` with async email — adding a ticket creation path alongside email is architecturally clean.

**Why it ranks below the others:** It improves the workflow but doesn't expand what's governed. Important for stickiness, less important for initial purchase decision.

---

## The Ranking

| Rank | Integration | Primary Value | Effort | Market Impact |
|---|---|---|---|---|
| **1** | **HR system (BambooHR)** | Answers "should they have access at all?" | Medium (2-3 weeks) | Transforms the product from access review tool to identity lifecycle platform |
| **2** | **SIEM / Syslog export** | Fits into existing security stack | Low (2-3 days) | Removes adoption blocker for security teams |
| **3** | **Google Workspace** | Expands governance beyond LDAP | Medium (1-2 weeks) | Covers the most common hybrid scenario |
| **4** | **ServiceNow / Jira** | Replaces email notifications with tickets | Medium (1-2 weeks) | Improves stickiness and audit evidence |

---

## The Argument for HR Over Everything Else

The access review feature answers: **"Is this access still appropriate?"**

The HR integration answers: **"Should this person exist in the system at all?"**

That second question is what auditors care about most. An orphaned account — someone who left the company but still has LDAP access — is the #1 finding in access audits. LDAPAdmin currently has no way to detect orphaned accounts because it doesn't know who's employed. It can tell you who's in a group, but not whether they should be.

With a BambooHR integration, the compliance dashboard from the roadmap gains its most powerful widget: **"5 accounts belong to terminated employees."** That single line item justifies the entire product purchase.

The revised pitch becomes:

> "Connect your LDAP directory and your HR system. We'll tell you who has access that shouldn't, run your certification campaigns, and export the evidence — for 1/5 the cost of enterprise IGA."

That's a fundamentally stronger product than "we review LDAP groups."

---

## What NOT to Integrate

| Integration | Why Not |
|---|---|
| AWS IAM / Azure RBAC | Too deep, too enterprise, wrong market segment |
| Slack / Teams notifications | Nice-to-have, not a purchase driver |
| SCIM provisioning endpoint | Huge effort, minimal governance value — read-only connectors cover 90% of the use case |
| Okta / Auth0 | These are identity providers, not access sources — if the customer has Okta, they probably don't need LDAPAdmin |
| Database connectors (MySQL, Postgres users) | Niche, fragmented, no standard API |
