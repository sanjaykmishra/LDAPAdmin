# LDAPAdmin Product Roadmap

## Position

"Pass your next access review audit in 30 days — for 1/5 the cost of enterprise IGA."

Target: Mid-size LDAP shops (500-10,000 identities) that need compliance without enterprise pricing.

## Current State

Working software with real access reviews, approval workflows, self-service, and audit logging. But the gap between "working software" and "product someone can buy and use on day one" is where every prospect drops off. Nine steps and 45-90 minutes to first value. No docs. CSV-only exports. No lifecycle automation.

---

## Phase 1: Make It Buyable (Weeks 1-4)

**Goal:** A new customer goes from `docker compose up` to auditor-ready evidence in under 30 minutes.

**Theme:** Close the gap between working software and sellable product.

| # | Item | Type | Effort | Why Now |
|---|---|---|---|---|
| 1.1 | **First-run setup wizard** | UX | 1 week | 9-step onboarding is the #1 reason prospects abandon. Walk them through: connect LDAP, test connection, pick a profile template, create first campaign. |
| 1.2 | **Admin quick-start guide** | Docs | 2-3 days | One page covering OpenLDAP, AD, and 389DS connection. How to run first access review. How to set up approvals. Probably matters more than any code change. |
| 1.3 | **Compliance report templates (PDF)** | Feature | 1 week | Three reports: User Access Report, Access Review Summary, Privileged Account Inventory. Exportable to PDF. These are the exact documents auditors ask for. CSV export exists but the gap between "audit log" and "audit evidence" is where customers feel pain. |
| 1.4 | **Group picker in campaign creation** | UX | 3-4 days | Users currently type group DNs by hand. Add a searchable group browser to the campaign creation form. Eliminates the most common friction point in the access review journey (Step 5). |
| 1.5 | **SIEM / syslog export** | Integration | 2-3 days | Syslog (RFC 5424) + CEF format + optional webhook. `AuditService` already records events async — add an output sink. Highest ROI-per-engineering-hour of any integration. Removes adoption blocker for security teams. |

**Exit criteria:** A new user can install, connect to LDAP, run an access review, and hand a PDF report to an auditor — all within 30 minutes, guided by the wizard and the quick-start guide.

**What changes for the customer:** Time-to-value drops from 45-90 minutes of guesswork to 15-30 minutes of guided setup. The product becomes demonstrable in a sales call.

---

## Phase 2: Make It Sticky (Weeks 5-10)

**Goal:** Customers who complete their first access review have reasons to stay and automate.

**Theme:** Answer every question an auditor asks.

| # | Item | Type | Effort | Why Now |
|---|---|---|---|---|
| 2.1 | **Separation of Duties (SoD) policy engine** | Feature | 2 weeks | The #1 feature auditors ask about that LDAPAdmin doesn't have. Define conflicting group pairs, detect violations on first scan, block future violations at assignment time, generate violations report. Implementation is group membership set intersection with a policy table — no ML required. |
| 2.2 | **Compliance posture dashboard** | Feature | 1 week | One screen: open campaign completion %, pending approval aging, SoD violation count, orphaned accounts, accounts not reviewed in 90+ days. The screen a CISO opens weekly. Every competitor has one. |
| 2.3 | **Scheduled access reviews with auto-reminders** | Feature | 1.5 weeks | Recurrence entity already exists (V23 migration) — wire it up. Auto-create quarterly campaigns. Escalation emails: 7 days no response -> remind, 14 days -> escalate. Auto-revoke on deadline if configured. Turns access reviews from manual process to automated compliance program. |
| 2.4 | **Evidence package export** | Feature | 1 week | One-click ZIP: access review campaigns + decisions + timestamps, SoD policy definitions + current violations, user entitlement snapshots, approval workflow history. Signed and timestamped. No competitor at this price point does this. |
| 2.5 | **Campaign templates** | UX | 3-4 days | Save campaign configurations (groups, reviewers, settings) as reusable templates. Currently every campaign is created from scratch. Reduces recurring campaign setup from 10 minutes to 30 seconds. |

**Exit criteria:** An auditor can receive a single evidence package covering access reviews, SoD policies, and approval history. The compliance dashboard shows posture at a glance. Campaigns run on autopilot.

**What changes for the customer:** LDAPAdmin stops being a tool they use quarterly and becomes a platform that runs continuously. Audit prep drops from a week-long scramble to a button click. This is where renewal decisions are made.

---

## Phase 3: Make It Indispensable (Weeks 11-18)

**Goal:** LDAPAdmin becomes the system of record for identity lifecycle, not just access reviews.

**Theme:** Answer "should this person have access at all?" — not just "is their current access appropriate?"

| # | Item | Type | Effort | Why Now |
|---|---|---|---|---|
| 3.1 | **HR system integration (BambooHR)** | Integration | 2-3 weeks | Read-only sync: employee list, status changes, department/role changes. Map employee -> LDAP DN by email or employee ID. Enables joiner/mover/leaver automation. Detects orphaned accounts (terminated employees with active LDAP access) — the #1 finding in access audits. Transforms the product from access review tool to identity lifecycle platform. |
| 3.2 | **Orphaned account detection + dashboard widget** | Feature | 1 week | Cross-reference HR active employees against LDAP accounts. Surface "5 accounts belong to terminated employees" on the compliance dashboard. This single widget justifies the product purchase for many buyers. Depends on 3.1. |
| 3.3 | **Entra ID connector (read-only)** | Integration | 2 weeks | Import users/groups alongside LDAP. Include Entra ID memberships in access reviews. Correlate identities across directories. Moves addressable market from "pure LDAP shops" to "hybrid shops migrating to cloud" — 5-10x larger. |
| 3.4 | **Reviewer context enrichment** | Feature | 1 week | Show reviewers: when the member was added to the group, what other groups they belong to, last login date, HR department (if HR integration active). Currently reviewers see only name and DN — not enough context for informed decisions. |
| 3.5 | **Cross-campaign reporting** | Feature | 1 week | "Show me all reviews for the past year." Aggregated view across campaigns: total decisions, revocation rate, average completion time, reviewer response times. Currently each export is per-campaign with no historical view. |

**Exit criteria:** LDAPAdmin knows who should have access (from HR), who does have access (from LDAP + Entra ID), and can surface the gaps automatically. Reviewers have context to make real decisions instead of rubber-stamping.

**What changes for the customer:** The pitch upgrades from "we review LDAP groups" to "connect your LDAP directory and your HR system — we'll tell you who has access that shouldn't." That's a fundamentally different product and a fundamentally stronger sale.

---

## Phase 4: Expand the Moat (Weeks 19-26)

**Goal:** Deepen integrations and make LDAPAdmin hard to replace.

**Theme:** Fit into the customer's existing operational stack.

| # | Item | Type | Effort | Why Now |
|---|---|---|---|---|
| 4.1 | **Google Workspace connector (read-only)** | Integration | 1-2 weeks | Google Admin SDK Directory API. Import users/groups. Include in access reviews. Covers the most common hybrid scenario for mid-size orgs. |
| 4.2 | **ServiceNow / Jira ticketing integration** | Integration | 1-2 weeks | Create tickets for approval requests and access review tasks. Uses existing `ApprovalNotificationService` architecture. Provides audit trail the auditor already trusts + SLA tracking. |
| 4.3 | **Workday / Personio HR connectors** | Integration | 1-2 weeks each | Extend HR integration beyond BambooHR. Workday covers 2,000+ employee segment. Personio covers EU mid-market. Reuses the connector architecture from 3.1. |
| 4.4 | **Automated joiner/mover/leaver workflows** | Feature | 2 weeks | When HR status changes: auto-provision on hire (using provisioning profiles), flag stale access on department change, auto-disable + trigger review on termination. Builds on HR integration (3.1) and lifecycle playbooks (already in codebase). |
| 4.5 | **API documentation (OpenAPI/Swagger)** | Docs | 1 week | Generate from existing Spring Boot controllers. Required for enterprise procurement. Enables customer-built integrations without support overhead. |

**Exit criteria:** LDAPAdmin governs LDAP + Entra ID + Google Workspace, receives lifecycle signals from HR, routes approvals through ticketing systems, and has documented APIs. The product is hard to rip out.

---

## What NOT to Build

| Feature | Why Not |
|---|---|
| AI-powered anomaly detection | Unproven ROI; auditors don't ask for it |
| SCIM provisioning endpoint | Huge effort, minimal governance value |
| Full write connectors for Entra/Okta/Google | Read-only covers 90% of the governance use case |
| Role mining / role engineering | Complex, niche, requires months of data to be useful |
| Mobile app | Nobody does access reviews on their phone |
| Chat/Slack integration | Nice-to-have, not a purchase driver |
| Multi-tenant SaaS hosting | Wrong order — product-market fit first, hosting later |
| AWS IAM / Azure RBAC connectors | Too deep, too enterprise, wrong market segment |
| Database connectors (MySQL/Postgres users) | Niche, fragmented, no standard API |

---

## Pricing (Ship After Phase 1)

| Tier | Users | Annual Price | Rationale |
|---|---|---|---|
| Small | < 500 | $2,000-3,000/yr | Competing with "just use spreadsheets" |
| Mid | 500-2,000 | $5,000-8,000/yr | Cheaper than an auditor's finding |
| Mid-Large | 2,000-5,000 | $8,000-15,000/yr | Still 1/3 the cost of SecurEnds |
| Large | 5,000-10,000 | $15,000-25,000/yr | Ceiling before SailPoint becomes justifiable |

Alternative: per-identity pricing at $1.50-3.00/identity/year.

**Price inflection points:**
- After Phase 2 (SoD + evidence export): +30-50% justified
- After Phase 3 (HR integration + Entra ID): +50-80% justified
- After Phase 4 (full hybrid governance): approach $3-5/identity/year

---

## Milestone Summary

```
Phase 1 (Weeks 1-4):   BUYABLE
  Setup wizard, quick-start guide, PDF reports, group picker, SIEM export
  -> New customer gets to value in 30 minutes

Phase 2 (Weeks 5-10):  STICKY
  SoD engine, compliance dashboard, scheduled reviews, evidence export, templates
  -> Auditor gets everything they need in one click

Phase 3 (Weeks 11-18): INDISPENSABLE
  BambooHR integration, orphaned account detection, Entra ID, reviewer context
  -> Product answers "should they have access?" not just "do they have access?"

Phase 4 (Weeks 19-26): DEFENSIBLE
  Google Workspace, ticketing, more HR connectors, JML automation, API docs
  -> Hard to rip out, covers the full hybrid identity landscape
```

---

## The Evolving Pitch

**After Phase 1:**
> "Pass your next access review audit in 30 days. Connect your LDAP directory, run your first certification campaign, and export auditor-ready evidence."

**After Phase 2:**
> "Automated access reviews with SoD enforcement and one-click audit evidence — for 1/5 the cost of enterprise IGA."

**After Phase 3:**
> "Connect your LDAP directory and your HR system. We'll tell you who has access that shouldn't, run your certification campaigns, and export the evidence."

**After Phase 4:**
> "Identity governance across LDAP, Entra ID, and Google Workspace — with HR-driven lifecycle automation and full audit evidence. Enterprise compliance without the enterprise price tag."

Each phase makes the previous pitch obsolete. That's how you know the sequencing is right.
