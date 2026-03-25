# Role-Based Access Drift / Privilege Creep Detection — Implementation Analysis

## The Problem

Users accumulate group memberships over time as they change roles, take on temporary projects, or get added to groups "just in case." Nobody removes the old access. After 3 years, a junior developer has the same LDAP group memberships as a senior DBA plus finance read access from when they helped with a budget report in 2023. Auditors flag this as "privilege creep" and it's the #1 finding in access audits.

## The Approach — Peer Group Comparison

No ML required. The logic is simple:

1. **Define peer groups** — users who share the same department, job title, or provisioning profile
2. **Compute the "normal" access set** for each peer group — the groups that >X% of peers belong to
3. **Flag outliers** — users who belong to groups that <Y% of their peers belong to
4. **Surface the delta** — "Alice is in 3 groups that nobody else in Engineering belongs to"

This is set intersection math, not machine learning.

---

## Data Model

```sql
-- Snapshot of user-to-group memberships taken periodically
CREATE TABLE access_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id    UUID        NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    captured_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, FAILED
    total_users     INTEGER,
    total_groups    INTEGER,
    completed_at    TIMESTAMPTZ
);

-- Individual membership records within a snapshot
CREATE TABLE access_snapshot_memberships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id     UUID        NOT NULL REFERENCES access_snapshots(id) ON DELETE CASCADE,
    user_dn         VARCHAR(1024) NOT NULL,
    group_dn        VARCHAR(1024) NOT NULL,
    group_name      VARCHAR(255)
);

CREATE INDEX idx_asm_snapshot ON access_snapshot_memberships (snapshot_id);
CREATE INDEX idx_asm_user ON access_snapshot_memberships (snapshot_id, user_dn);
CREATE INDEX idx_asm_group ON access_snapshot_memberships (snapshot_id, group_dn);

-- Peer group definitions (how to group users for comparison)
CREATE TABLE peer_group_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id    UUID        NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    -- What LDAP attribute defines the peer group (e.g., "department", "title", "ou")
    grouping_attribute VARCHAR(100) NOT NULL,
    -- Threshold: a group is "normal" for this peer group if >= this % of peers are in it
    normal_threshold_pct INTEGER NOT NULL DEFAULT 50,
    -- Threshold: flag a user if they're in a group that < this % of peers are in
    anomaly_threshold_pct INTEGER NOT NULL DEFAULT 10,
    enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Detected drift/anomalies from the most recent analysis
CREATE TABLE access_drift_findings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id     UUID        NOT NULL REFERENCES access_snapshots(id) ON DELETE CASCADE,
    rule_id         UUID        NOT NULL REFERENCES peer_group_rules(id) ON DELETE CASCADE,
    user_dn         VARCHAR(1024) NOT NULL,
    user_display    VARCHAR(255),
    peer_group_value VARCHAR(255) NOT NULL,  -- e.g., "Engineering" (the department value)
    peer_group_size INTEGER     NOT NULL,
    -- The anomalous group membership
    group_dn        VARCHAR(1024) NOT NULL,
    group_name      VARCHAR(255),
    -- What % of peers are in this group (low = more anomalous)
    peer_membership_pct DOUBLE PRECISION NOT NULL,
    -- Severity based on how far outside the norm
    severity        VARCHAR(10) NOT NULL,    -- HIGH, MEDIUM, LOW
    -- Status tracking
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, ACKNOWLEDGED, RESOLVED, EXEMPTED
    acknowledged_by UUID REFERENCES accounts(id),
    acknowledged_at TIMESTAMPTZ,
    exemption_reason TEXT,
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_adf_snapshot ON access_drift_findings (snapshot_id);
CREATE INDEX idx_adf_status ON access_drift_findings (status);
CREATE INDEX idx_adf_user ON access_drift_findings (user_dn);
```

The key insight: **we don't need to track historical changes over time** for the initial version. A single snapshot comparison against peers tells you everything. Historical trending is a Phase 2 enhancement.

---

## Algorithm

```
For each enabled peer_group_rule:
  1. Query LDAP for all users with the grouping attribute
     e.g., all users with (department=*)

  2. Bucket users by attribute value
     e.g., {"Engineering": [alice, bob, carol], "Finance": [dave, eve]}

  3. For each peer group (bucket):
     a. Get all group memberships for every user in the bucket
     b. Count: for each group, what % of peers are members?
        e.g., cn=devs: 3/3 = 100%, cn=vpn: 2/3 = 67%, cn=finance-ro: 1/3 = 33%
     c. The "normal set" = groups where membership % >= normal_threshold_pct
     d. For each user, find groups they're in where membership % < anomaly_threshold_pct
     e. Each such group is a "drift finding"

  4. Compute severity:
     - HIGH: user is the ONLY person in their peer group with this membership (0-5%)
     - MEDIUM: < anomaly_threshold_pct of peers (5-15%)
     - LOW: below normal but above anomaly (15-50%)

  5. Persist findings, de-duplicating against existing OPEN findings
```

**Example output:**

```
Alice (Engineering, peer group size: 47)
  ⚠ HIGH: cn=finance-readonly — only 2% of Engineering peers (1/47)
  ⚠ HIGH: cn=hr-reports — only 0% of Engineering peers (0/47, she's the only one)
  ⚠ MEDIUM: cn=legacy-vpn — only 8% of Engineering peers (4/47)

Bob (Engineering, peer group size: 47)
  ✓ No anomalous access detected
```

---

## Backend Components

### 1. `AccessSnapshotService`

```java
@Service
public class AccessSnapshotService {

    // Captures a full membership snapshot from LDAP
    @Transactional
    public AccessSnapshot captureSnapshot(UUID directoryId) {
        // 1. Create snapshot record (IN_PROGRESS)
        // 2. For each group in directory, get all members
        // 3. Store each (user_dn, group_dn) pair in access_snapshot_memberships
        // 4. Mark snapshot COMPLETED with counts
        // Capped at 50K users / 10K groups to prevent OOM
    }
}
```

### 2. `AccessDriftAnalysisService`

```java
@Service
public class AccessDriftAnalysisService {

    // Runs drift detection against the latest snapshot
    @Transactional
    public DriftAnalysisResult analyze(UUID directoryId, UUID snapshotId) {
        // 1. Load all peer group rules for directory
        // 2. For each rule, query LDAP for the grouping attribute
        // 3. Bucket users by attribute value
        // 4. For each bucket, compute per-group membership percentages
        // 5. Flag anomalies below threshold
        // 6. Persist findings, de-dup against existing OPEN findings
        // Returns: summary stats (total findings, by severity, top offenders)
    }

    // Returns findings for display/export
    public List<DriftFindingDto> getFindings(UUID directoryId, DriftFindingStatus status);

    // Acknowledge/exempt a finding
    public void acknowledgeFinding(UUID findingId, AuthPrincipal principal);
    public void exemptFinding(UUID findingId, String reason, AuthPrincipal principal);
}
```

### 3. `AccessDriftScheduler`

```java
@Component
public class AccessDriftScheduler {

    // Daily: capture snapshot + run analysis
    @Scheduled(cron = "${ldapadmin.drift.analysis-cron:0 0 4 * * ?}")
    public void scheduledAnalysis() {
        for (DirectoryConnection dir : enabledDirectories) {
            AccessSnapshot snapshot = snapshotService.captureSnapshot(dir.getId());
            analysisService.analyze(dir.getId(), snapshot.getId());
        }
    }
}
```

### 4. `AccessDriftController`

```
GET  /api/v1/directories/{dirId}/drift/rules          — list peer group rules
POST /api/v1/directories/{dirId}/drift/rules          — create rule
PUT  /api/v1/directories/{dirId}/drift/rules/{ruleId} — update rule
DELETE /api/v1/directories/{dirId}/drift/rules/{ruleId}

POST /api/v1/directories/{dirId}/drift/analyze        — trigger analysis now
GET  /api/v1/directories/{dirId}/drift/findings        — list findings (filterable)
GET  /api/v1/directories/{dirId}/drift/findings/summary — counts by severity
POST /api/v1/directories/{dirId}/drift/findings/{id}/acknowledge
POST /api/v1/directories/{dirId}/drift/findings/{id}/exempt

GET  /api/v1/directories/{dirId}/drift/snapshots       — snapshot history
```

---

## Integration Points with Existing Code

**Dashboard** — Add to `ComplianceDashboardDto`:
```java
long accessDriftFindingsHigh,
long accessDriftFindingsMedium,
long accessDriftFindingsTotal
```

With a clickable card that links to the drift findings view.

**Evidence Package** — Add `addDriftFindings()` section to `EvidencePackageService`:
- Current open/acknowledged findings as JSON
- Peer group rule definitions
- Summary statistics

**Auditor Portal** — Include drift findings as a browseable section.

**Reports** — Add `ACCESS_DRIFT` to `ReportType` enum for CSV/PDF export of findings.

**SoD integration** — Drift findings can cross-reference SoD policies: "Alice's anomalous finance-readonly membership also violates the Finance vs Engineering SoD policy."

---

## HR Integration Enhancement

If BambooHR integration is active, use `department` and `jobTitle` from `hr_employees` as the peer grouping attributes instead of LDAP attributes. This is more reliable because:
1. LDAP `department` attributes are often stale/missing
2. HR is the system of record for org structure
3. It connects drift detection to the employee lifecycle — "Alice moved from Finance to Engineering 6 months ago but still has Finance groups"

The algorithm stays the same; only the data source for peer grouping changes.

---

## Frontend

### Peer Group Rules Management

Simple CRUD form:
- Name, grouping attribute (dropdown: department, title, ou, or custom), thresholds
- Toggle enabled/disabled

### Drift Findings View

The main value screen:

```
┌─────────────────────────────────────────────────────────┐
│  Access Drift Detection                    [Run Now]    │
│  Last analysis: 2 hours ago  │  47 findings             │
├─────────────────────────────────────────────────────────┤
│  ┌─────────┐ ┌─────────┐ ┌─────────┐                   │
│  │  12     │ │  23     │ │  12     │                   │
│  │  HIGH   │ │  MEDIUM │ │  LOW    │                   │
│  └─────────┘ └─────────┘ └─────────┘                   │
├─────────────────────────────────────────────────────────┤
│  Filter: [All ▾] [All severities ▾] [Search user...]   │
│                                                         │
│  ⚠ HIGH  Alice Chen (Engineering, 47 peers)             │
│    cn=finance-readonly — 2% of peers (1/47)             │
│    cn=hr-reports — 0% of peers (unique)                 │
│    [Acknowledge] [Exempt]                               │
│                                                         │
│  ⚠ HIGH  Dave Kim (Finance, 12 peers)                   │
│    cn=devops-deploy — 0% of peers (unique)              │
│    [Acknowledge] [Exempt]                               │
│                                                         │
│  ⚠ MED   Carol Wu (Engineering, 47 peers)               │
│    cn=legacy-vpn — 8% of peers (4/47)                   │
│    [Acknowledge] [Exempt]                               │
└─────────────────────────────────────────────────────────┘
```

### User Detail Drill-down

Click a user to see:
- All their group memberships
- Which are "normal" for their peer group (green)
- Which are anomalous (red/yellow with peer % shown)
- If HR integration active: their department history and when the access was likely accumulated

---

## Performance Considerations

**Snapshot capture** is the most expensive operation — it queries every group for its members. Mitigations:
- Cap at 50K users / 10K groups (same limits used elsewhere)
- Use `"1.1"` attribute (DN-only) to minimize data transfer
- Run at 4 AM daily when directory load is low
- Cache the snapshot in the DB — analysis can re-run against it without re-querying LDAP

**Analysis** is pure in-memory set math against the snapshot DB tables — no LDAP calls needed after the snapshot is captured. For 10K users across 500 groups, the analysis completes in seconds.

**Storage** — Each snapshot stores `users × avg_groups_per_user` rows. For 5000 users averaging 8 groups each = 40K rows per snapshot. At one snapshot per day, that's ~1.2M rows per month. A retention policy (keep last 90 snapshots, purge older) keeps this manageable.

---

## Implementation Phases

### Phase 1 — Core detection (5-6 days)
- Migration: 4 tables
- Entities + Repositories
- `AccessSnapshotService`: capture from LDAP, store in DB
- `AccessDriftAnalysisService`: peer bucketing, threshold comparison, finding creation
- `AccessDriftScheduler`: daily cron
- Controller: rules CRUD, trigger analysis, list findings, acknowledge/exempt
- Basic frontend: rules config, findings list with severity badges

### Phase 2 — Integration (2-3 days)
- Dashboard widget (finding counts by severity)
- Evidence package section
- Report type (`ACCESS_DRIFT`)
- Audit trail for all operations
- HR-based peer grouping (use department from `hr_employees`)

### Phase 3 — Trending (2-3 days)
- Historical comparison: "Alice gained 3 new groups since last quarter"
- Per-user drift timeline
- "Access growth rate" metric on dashboard
- SoD cross-reference in findings

**Total: ~10-12 days**

---

## What Makes This Valuable

1. **No competitor at this price point does it** — SailPoint has it, but costs 10x more. Mid-market tools don't.
2. **It's the #1 audit finding** — privilege creep appears in literally every SOX and SOC 2 audit. Having an automated answer to "how do you detect privilege creep?" is a procurement checkbox.
3. **It makes access reviews smarter** — instead of reviewing 200 identical rows, reviewers can focus on the flagged anomalies. Connects directly to the "review fatigue" problem.
4. **No ML, no training period** — works on day one with a single snapshot. The peer comparison is immediately meaningful.
5. **It compounds with HR integration** — "This user changed departments 6 months ago and still has their old department's access" is the killer finding that auditors love.
