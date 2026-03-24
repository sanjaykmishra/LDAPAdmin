# LDAPAdmin: Owning the Niche

## Target Niche

**"LDAP governance for orgs that can't justify SailPoint."**

Mid-size LDAP shops (500-10,000 identities) that need compliance without enterprise pricing. Universities, government agencies, mid-market companies that failed an audit or have a compliance deadline approaching.

## Principle: Time-to-Value is Everything

The product that gets a customer from "install" to "here's my access review evidence" fastest wins. Current time-to-value is too long — no setup wizard, no docs, no pre-built reports.

---

## Tier 1: Ship Now (Highest Impact, Lowest Effort)

These aren't features — they're the gap between "working software" and "product."

### 1. First-Run Setup Wizard

When someone opens LDAPAdmin for the first time, walk them through:

- Connect to your LDAP directory (test connection inline)
- Pick a provisioning profile (or auto-detect schema)
- Create your first access review campaign
- Send yourself a test notification

Turns a 2-hour fumble into a 15-minute win.

### 2. Pre-Built Compliance Report Templates

Three reports, exportable to PDF:

- **User Access Report** — who has access to what groups, when granted
- **Access Review Summary** — campaign results, decisions, revocations
- **Privileged Account Inventory** — members of admin groups with last activity

These are the exact documents auditors ask for. The gap between "audit log" and "audit evidence" is where customers feel pain.

### 3. Admin Quick-Start Guide

Not comprehensive docs. One page:

- How to connect to OpenLDAP / Active Directory / 389DS
- How to run your first access review
- How to set up approval workflows
- How to configure self-service

This single document probably matters more than any code change.

---

## Tier 2: Build Next (Core Differentiators)

Features that make a customer choose LDAPAdmin over spreadsheets AND stay past the first renewal.

### 4. Separation of Duties (SoD) Policy Engine

Simple version:

```
Rule: No user may be in both "Finance-Admins" AND "Finance-Approvers"
Action: Alert / Block / Require exception approval
```

- Define conflicting group pairs
- Detect existing violations on first scan
- Block future violations at assignment time
- Generate a violations report for auditors

The #1 feature auditors ask about that LDAPAdmin doesn't have. Relatively simple to implement — group membership set intersection with a policy table. No ML required.

### 5. Scheduled Access Reviews with Auto-Reminders

The entity already supports recurrence. Wire it up:

- Quarterly review campaigns that auto-create
- Escalation emails: reviewer hasn't responded in 7 days -> remind, 14 days -> escalate to manager
- Auto-revoke if no decision by deadline (configurable)

Turns access reviews from a manual process into an automated compliance program. The difference between "tool" and "platform."

### 6. Dashboard with Compliance Posture

One screen showing:

- Open review campaigns and completion percentage
- Pending approvals aging (how long have they been waiting?)
- SoD violations count
- Orphaned accounts (accounts with no group membership)
- Accounts not reviewed in 90+ days

The screen a CISO opens once a week. Every competitor has one. LDAPAdmin doesn't.

---

## Tier 3: Strategic Advantage (Moat Builders)

### 7. Entra ID Connector (Read-Only to Start)

Not full provisioning — just:

- Import Entra ID users/groups alongside LDAP
- Include Entra ID memberships in access reviews
- Correlate identities across both directories

Moves the addressable market from "pure LDAP shops" to "hybrid shops migrating to cloud" — 5-10x larger. Read-only is fine initially; governance doesn't require write access.

### 8. Evidence Package Export

One-click export of everything an auditor needs:

- Access review campaigns + decisions + timestamps
- SoD policy definitions + current violations
- User entitlement snapshots at point-in-time
- Approval workflow history
- All in a signed, timestamped ZIP

No competitor at this price point does this. Turns audit prep from a week-long scramble into a button click.

---

## What NOT to Build

| Feature | Why Not |
|---|---|
| AI-powered anomaly detection | Interesting but unproven ROI; auditors don't ask for it |
| SCIM provisioning | Huge engineering effort, minimal governance value |
| Full write connector for Entra/Okta | Read-only covers 90% of the governance use case |
| Role mining / role engineering | Complex, niche, requires months of data to be useful |
| Mobile app | Nobody does access reviews on their phone |
| Chat/Slack integration | Nice-to-have, not a purchase driver |
| Multi-tenant SaaS hosting | Wrong order — product-market fit first, hosting later |

---

## Sequencing for Maximum Impact

```
Month 1:  Setup wizard + quick-start guide + compliance reports
          (Time-to-value goes from hours to minutes)

Month 2:  SoD policy engine + compliance dashboard
          (Answers the two questions every auditor asks)

Month 3:  Scheduled reviews with auto-reminders + evidence export
          (Turns a tool into an automated compliance program)

Month 4:  Entra ID read-only connector
          (Expands the market 5-10x)
```

After month 1, you have a sellable product. After month 3, you have a sticky one. After month 4, you have a growing one.

---

## Pricing (Mid-Size LDAP Shops)

| Tier | Users | Annual Price | Rationale |
|---|---|---|---|
| Small | < 500 | $2,000-3,000/yr | Competing with "just use spreadsheets" |
| Mid | 500-2,000 | $5,000-8,000/yr | Cheaper than an auditor's finding |
| Mid-Large | 2,000-5,000 | $8,000-15,000/yr | Still 1/3 the cost of SecurEnds |
| Large | 5,000-10,000 | $15,000-25,000/yr | Ceiling before SailPoint becomes justifiable |

Alternative: per-identity pricing at $1.50-3.00/identity/year.

---

## The Pitch

> "Pass your next access review audit in 30 days. Connect your LDAP directory, run your first certification campaign, and export auditor-ready evidence — for 1/5 the cost of enterprise IGA."

Every feature above serves that sentence. If a feature doesn't, it shouldn't be in the roadmap.
