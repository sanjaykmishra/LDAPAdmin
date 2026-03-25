# Compliance-First Product Pivot: SOC 2 / SOX Focus

## Current State Inventory

LDAPAdmin already has significant compliance infrastructure. Here's an honest assessment of what exists, what's incomplete, and what's missing.

### What Works Today

| Capability | Maturity | Notes |
|-----------|----------|-------|
| **Audit trail** | Solid | 76+ event types, JSONB detail, actor/target/timestamp. Queryable by directory, actor, action, target, date range. |
| **Access review campaigns** | Solid | Full lifecycle (UPCOMING→ACTIVE→CLOSED/EXPIRED), per-group reviewer assignment, CONFIRM/REVOKE decisions, recurring campaigns, auto-revoke on expiry. |
| **SoD policies** | Solid | Group-pair conflict detection, ALERT or BLOCK enforcement, real-time check on group membership changes, full scan capability, exemption workflow with reason tracking. |
| **Evidence package** | Solid | ZIP with PDF reports, campaign decisions CSV, SoD data, approval history, user entitlements, HMAC-signed manifest with SHA-256 checksums. |
| **Cross-campaign reports** | Solid | Aggregated metrics across campaigns: revocation rates, reviewer response times, completion %. PDF and CSV export. |
| **PDF reports** | Good | User Access Report, Access Review Summary, Privileged Account Inventory. Branded with app name. |
| **Approval workflows** | Good | Configurable per-profile, self-approval prevention, approval editing, auto-escalation, audit trail. |
| **SIEM export** | Basic | Async export of all audit events. UDP/TCP syslog + HTTPS webhook. CEF/JSON formatting. |
| **Compliance dashboard** | Basic | SoD violations, campaign completion %, overdue campaigns, approval aging, users not reviewed in 90 days. |
| **Scheduled reports** | Partial | Entity and CRUD exist, but **scheduler execution is not implemented** — reports don't actually run on schedule. S3 delivery is declared but **upload code is missing**. |
| **LDAP changelog polling** | Good | Captures external LDAP changes for audit completeness. |

### What's Missing for a Compliance-First Product

The gaps fall into four categories: **auditor experience**, **continuous monitoring**, **framework mapping**, and **evidence automation**.

---

## Gap Analysis & Recommendations

### 1. Auditor-Ready Reporting (Highest Priority)

**Problem**: The current reports are operational tools for admins, not evidence artifacts for auditors. An auditor doing a SOC 2 Type II or SOX ITGC review needs specific deliverables that map to control objectives.

#### 1a. Control-Mapped Report Templates

SOC 2 Trust Services Criteria (TSC) and SOX ITGC controls have specific evidence requirements. Build report templates that directly produce what auditors ask for:

| SOC 2 Control | What Auditors Ask For | Current State | Gap |
|---------------|----------------------|---------------|-----|
| **CC6.1** — Logical access security | List of all users and their access rights | User Access Report (PDF) exists | Needs point-in-time snapshot capability, not just current state |
| **CC6.2** — New user provisioning | Evidence that new accounts go through approval | Approval history exists | No report that shows "100% of new accounts in period X went through approval" |
| **CC6.3** — Access removal on termination | Evidence that terminated users are deprovisioned promptly | Orphaned account detection (HR sync) exists | No report showing median time-to-deprovision or accounts disabled within N days of termination |
| **CC6.5** — Periodic access reviews | Evidence reviews were completed on time | Campaign data exists | Cross-campaign report doesn't show control effectiveness over time (trend) |
| **CC6.6** — Privileged access | Inventory of admin accounts and justification | Privileged Account Inventory exists | No change log for privilege grants/revocations; no justification field |
| **CC8.1** — Change management | Evidence that directory changes follow process | Audit trail exists | No report that correlates changes to approved requests |

**Deliverable**: A new `ComplianceReportTemplate` system with pre-built templates for SOC 2 CC6.x and SOX ITGC controls. Each template defines:
- Control ID and description
- Data sources (audit events, campaigns, approvals, LDAP snapshots)
- Output format (PDF with control narrative + evidence table)
- Assertion text (e.g., "All user provisioning events during the review period required and received approval")

#### 1b. Point-in-Time Access Snapshots

**Problem**: Auditors ask "who had access to what on date X?" The current system can only answer "who has access right now."

**Solution**: Periodic LDAP snapshots stored in PostgreSQL.

```sql
CREATE TABLE access_snapshots (
    id              UUID PRIMARY KEY,
    directory_id    UUID NOT NULL REFERENCES directory_connections(id),
    captured_at     TIMESTAMPTZ NOT NULL,
    triggered_by    VARCHAR(20) NOT NULL, -- SCHEDULED, MANUAL, CAMPAIGN_START
    user_count      INT NOT NULL,
    group_count     INT NOT NULL,
    data_hash       VARCHAR(64) NOT NULL  -- SHA-256 of snapshot data
);

CREATE TABLE access_snapshot_entries (
    snapshot_id     UUID NOT NULL REFERENCES access_snapshots(id) ON DELETE CASCADE,
    user_dn         VARCHAR(2048) NOT NULL,
    group_dn        VARCHAR(2048) NOT NULL,
    group_name      VARCHAR(255)
);
CREATE INDEX idx_snapshot_entries ON access_snapshot_entries(snapshot_id, user_dn);
```

- Scheduled daily (configurable)
- Auto-captured at campaign start/close for before/after comparison
- Queryable: "show me user X's group memberships as of date Y"
- Diff capability: "what changed between snapshot A and snapshot B"

#### 1c. Audit Log Completeness Report

Auditors need assurance that the audit trail itself is reliable. Add:

- **Gap detection**: Alert when there are unexplained time gaps in audit events (e.g., no events for 6+ hours during business hours)
- **Tamper evidence**: The evidence package already has HMAC signing — extend this to audit log exports
- **Retention reporting**: Show that logs are retained for the required period (typically 1 year for SOC 2, 7 years for SOX)

---

### 2. Continuous Compliance Monitoring (High Priority)

**Problem**: Compliance is currently point-in-time (run a scan, generate a report). Auditors increasingly expect continuous monitoring with alerting.

#### 2a. Compliance Score / Posture Dashboard

The current dashboard shows raw numbers. Transform it into a **compliance posture score** that executives and auditors can understand at a glance:

```
Overall Compliance Score: 87/100

Control Areas:
  Access Reviews     ████████░░  82%  (1 overdue campaign)
  SoD Compliance     █████████░  94%  (3 open violations, all ALERT severity)
  Provisioning       ██████████  100% (all new accounts approved)
  Deprovisioning     ███████░░░  71%  (4 orphaned accounts, median 3.2 days to disable)
  Privileged Access  █████████░  95%  (1 admin with no login in 90 days)
```

Each sub-score is computed from concrete metrics:
- **Access Reviews**: % campaigns completed on time, % decisions made before deadline
- **SoD**: % policies with zero open violations, severity-weighted violation score
- **Provisioning**: % new accounts that went through approval workflow
- **Deprovisioning**: Median time-to-disable after termination (from HR sync), orphaned account count
- **Privileged Access**: Admin accounts with recent activity, MFA coverage, permission scope

#### 2b. Compliance Alerts

Real-time notifications for compliance-relevant events (separate from operational alerts):

| Alert | Trigger | SOC 2 Control |
|-------|---------|---------------|
| Access review deadline approaching | Campaign at 80%+ time, <50% decisions | CC6.5 |
| SoD violation detected (BLOCK severity) | Real-time on group membership change | CC6.1 |
| Orphaned account detected | HR sync finds terminated employee with active LDAP | CC6.3 |
| Privileged account created | New SUPERADMIN or ADMIN account | CC6.6 |
| Approval request stale >7 days | Pending approval aging | CC6.2 |
| Audit log gap detected | No events for >6 hours during business hours | CC7.2 |
| Bulk operation without approval | Bulk import bypassing approval workflow | CC8.1 |

These should be surfaceable via:
- Dashboard banner (already partially exists)
- Email digest (daily compliance summary)
- SIEM export (already exists, needs alert-level tagging)
- Webhook (already exists)

#### 2c. Scheduled Report Execution (Fix Existing Gap)

The `ScheduledReportJob` entity exists but **reports don't actually run on schedule**. The `ScheduledReportJobService` has CRUD but no scheduler polls and executes them. This is a critical gap — auditors expect recurring evidence generation.

**Fix**: Add a `ReportScheduler` (same pattern as `HrSyncScheduler` and `AccessReviewScheduler`):
- Poll every 60 seconds
- Check cron expression against `lastRunAt`
- Execute report via `ReportExecutionService.run()`
- Deliver via EMAIL (using existing SMTP) or S3 (implement `S3Client.putObject()`)
- Update `lastRunAt`, `lastRunStatus`, `lastRunMessage`

Also implement the missing S3 upload for report archival — auditors expect evidence to be stored in immutable storage.

---

### 3. Framework Mapping & Control Narratives (Medium Priority)

**Problem**: The product speaks "LDAP" when it needs to speak "controls." An auditor doesn't care about `memberOf` attributes — they care about "logical access controls are in place."

#### 3a. Control Framework Registry

Add a first-class `ComplianceFramework` concept:

```sql
CREATE TABLE compliance_frameworks (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,  -- 'SOC 2 Type II', 'SOX ITGC', 'ISO 27001'
    version     VARCHAR(20),
    enabled     BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE compliance_controls (
    id              UUID PRIMARY KEY,
    framework_id    UUID NOT NULL REFERENCES compliance_frameworks(id),
    control_id      VARCHAR(50) NOT NULL,   -- 'CC6.1', 'ITGC-AC-01'
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    evidence_types  TEXT[],                  -- ['access_review', 'approval_history', 'sod_scan']
    UNIQUE (framework_id, control_id)
);

CREATE TABLE control_evidence_mappings (
    control_id      UUID REFERENCES compliance_controls(id),
    evidence_source VARCHAR(50) NOT NULL,    -- 'access_review_campaign', 'sod_scan', 'approval_log', etc.
    description     TEXT,
    PRIMARY KEY (control_id, evidence_source)
);
```

Ship with pre-loaded SOC 2 CC6.x and SOX ITGC mappings. This lets the UI show:

> **CC6.1 — Logical Access Security**
>
> Evidence sources: User Access Report, SoD Policies, Provisioning Profile Config
>
> Status: 3 of 3 evidence sources active
> Last generated: 2026-03-24

#### 3b. Control Narrative Templates

For each control, provide editable narrative templates that auditors can customize:

> "The organization uses LDAPAdmin to manage logical access to LDAP directory services. Access is provisioned through provisioning profiles that enforce required object classes, target OUs, and group assignments. All user creation requests require approval from a designated approver before the LDAP entry is created. During the review period [START] to [END], [COUNT] user creation requests were submitted, of which [APPROVED] were approved, [REJECTED] were rejected, and [PENDING] are pending."

The system fills in `[PLACEHOLDERS]` with actual data from the review period.

---

### 4. Evidence Automation & Packaging (Medium Priority)

#### 4a. Automated Evidence Collection Runs

Instead of ad-hoc evidence package generation, schedule recurring evidence collection:

- **Monthly**: Access snapshots, SoD scan results, approval statistics
- **Quarterly**: Cross-campaign report, privileged account changes, compliance score trend
- **Annually**: Full evidence package for audit period

Store in S3 with immutable object lock (WORM compliance) if supported by the S3 backend.

#### 4b. Evidence Package Enhancements

The existing `EvidencePackageService` is solid. Enhance it with:

- **Audit log excerpt**: Include filtered audit events for the review period (not just approval history)
- **Control mapping index**: A cover page listing which files satisfy which controls
- **Diff from previous package**: "What changed since last evidence collection"
- **Automated assertions**: Machine-generated statements like "100% of user creation events during the period had corresponding approval records" (verifiable from the data in the package)
- **Digital signature**: The HMAC signing is good; add an option for the generating user's name and timestamp to be cryptographically bound to the manifest

#### 4c. Audit Trail Export

The audit log is queryable via API but there's no bulk export for long-term archival:

- CSV/JSON export of audit events for a date range
- Scheduled export to S3 (daily, append-only)
- Retention policy enforcement: auto-delete events older than configured retention period (with warning before deletion)

---

### 5. UX Shift: Compliance-First Navigation (Lower Priority but High Impact)

**Problem**: The current navigation is organized around LDAP operations (Users, Groups, Bulk, Schema). A compliance buyer navigates by control objective, not by LDAP operation.

#### 5a. Dual Navigation Modes

Keep the current "Admin" navigation for day-to-day operations. Add a "Compliance" navigation mode:

**Compliance Nav:**
```
📊 Compliance Dashboard
   └─ Score breakdown, alerts, trends

📋 Access Reviews
   ├─ Active Campaigns
   ├─ Campaign History
   ├─ Cross-Campaign Report
   └─ Templates

🔒 Separation of Duties
   ├─ Policies
   ├─ Violations
   └─ Scan History

📝 Approval Audit
   ├─ Pending Requests
   ├─ Approval History
   └─ Approval Statistics

📄 Reports & Evidence
   ├─ Compliance Reports
   ├─ Scheduled Jobs
   ├─ Evidence Packages
   └─ Access Snapshots

🏗️ Control Framework
   ├─ SOC 2 Controls
   ├─ SOX ITGC Controls
   └─ Custom Controls

👥 HR Reconciliation
   ├─ Employee Sync Status
   └─ Orphaned Accounts
```

#### 5b. Auditor Portal (Read-Only)

A dedicated read-only view for external auditors with:
- Scoped access (specific date range, specific directories)
- Pre-generated evidence packages
- Control-mapped evidence browser
- No ability to modify data

This could be implemented as a new `AccountRole.AUDITOR` with its own route guard and restricted API access.

---

## Implementation Priority

### Phase 1: Fix the Broken, Ship the Quick Wins (2-3 weeks)

1. **Fix scheduled report execution** — The entity and UI exist; just add the scheduler that actually runs reports
2. **Implement S3 upload** — Required for report archival and evidence storage
3. **Add audit log CSV/JSON export** — Simple endpoint, high auditor value
4. **Add point-in-time access snapshots** — Daily scheduled + on campaign start/close
5. **Compliance alert emails** — Daily digest of compliance-relevant events

### Phase 2: Auditor-Ready Reporting (3-4 weeks)

6. **Control-mapped report templates** — SOC 2 CC6.x and SOX ITGC pre-built reports
7. **Compliance posture score** — Computed from existing data, displayed on dashboard
8. **Evidence package enhancements** — Audit log excerpt, control mapping index, automated assertions
9. **Provisioning compliance report** — "All new accounts had approval" evidence
10. **Deprovisioning timeliness report** — Median time-to-disable, orphaned account history

### Phase 3: Framework & Automation (3-4 weeks)

11. **Control framework registry** — SOC 2 and SOX pre-loaded, custom framework support
12. **Control narrative templates** — Auto-populated with period-specific data
13. **Automated evidence collection** — Scheduled monthly/quarterly evidence runs
14. **Compliance score trending** — Historical score tracking with period-over-period comparison
15. **Auditor portal** — Read-only role with scoped access

### Phase 4: Product Positioning (Ongoing)

16. **Rebrand navigation** — Compliance-first nav mode alongside admin mode
17. **Landing page / marketing** — Position as "LDAP Compliance Platform" not "LDAP Admin Tool"
18. **Integrations** — GRC tool export (ServiceNow GRC, Vanta, Drata APIs)
19. **Certification** — Consider Vanta/Drata integration partner listing

---

## Key Technical Decisions

| Decision | Recommendation | Rationale |
|----------|---------------|-----------|
| **Where to store snapshots?** | PostgreSQL + optional S3 archival | Snapshots need to be queryable (joins with campaigns); archive to S3 after 90 days |
| **How to compute compliance score?** | Materialized in DB, refreshed hourly | Avoid computing on every dashboard load (hits LDAP for user counts) |
| **Report template engine?** | Extend existing OpenPDF + add Thymeleaf for HTML | OpenPDF already works; Thymeleaf handles narrative templates cleanly |
| **Auditor role implementation?** | New `AccountRole.AUDITOR` + route guard | Cleaner than shoehorning into READ_ONLY role |
| **GRC integration format?** | Start with CSV/JSON export; add API later | Most GRC tools import flat files; API integration is customer-specific |

---

## What to De-emphasize

To shift focus, these areas should move to maintenance mode (bug fixes only):

- **Schema browser** — Useful for admins, not compliance buyers
- **LDIF import** — Low-level tool, not compliance-relevant
- **Self-service portal** — Important but not the differentiator
- **Directory browser (tree view)** — Admin tool, not compliance tool
- **Bulk attribute updates** — Operational, not compliance
- **Provisioning profile attribute configuration** — Keep working, don't add features

The LDAP management capabilities are the *foundation* — they need to work reliably but shouldn't be the *headline*. The headline is: "Continuous access compliance for LDAP directories — SOC 2 and SOX ready out of the box."
