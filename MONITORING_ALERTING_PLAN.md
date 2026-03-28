# Continuous Access Monitoring & Alerting Framework — Implementation Plan

## Problem

LDAPAdmin has strong governance capabilities (access reviews, SoD policies, audit logging, approval workflows) but no continuous monitoring that proactively surfaces access-related risks between scheduled reviews. Administrators must manually check dashboards to discover issues.

## Solution

A **Monitoring Engine** that continuously evaluates configurable alert rules against directory and application data, creating alert instances when conditions are met and delivering notifications via in-app and email channels.

## Design Principles

- **Built-in rules with configurable thresholds** — ship with ~25 pre-defined rule types; admins enable and tune them per directory
- **Deduplication** — don't re-alert on the same condition until it's acknowledged or resolved
- **Severity levels** — CRITICAL, HIGH, MEDIUM, LOW drive visual treatment and notification urgency
- **Leverage existing infrastructure** — Spring `@Scheduled`, `NotificationService`, SMTP, `AuditEventRepository`, existing scanners
- **No LDAP writes** — monitoring is strictly read-only

---

## Phase 1: Data Model

### 1a. Migration: `V57__alert_rules_and_instances.sql`

```sql
-- Alert rule definitions (one per enabled check per directory)
CREATE TABLE alert_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id    UUID REFERENCES directory_connections(id) ON DELETE CASCADE,
    rule_type       VARCHAR(80) NOT NULL,       -- e.g. 'SOD_VIOLATION_NEW'
    enabled         BOOLEAN NOT NULL DEFAULT true,
    severity        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',  -- CRITICAL, HIGH, MEDIUM, LOW
    params          JSONB NOT NULL DEFAULT '{}', -- thresholds, e.g. {"days": 90, "threshold": 5}
    notify_in_app   BOOLEAN NOT NULL DEFAULT true,
    notify_email    BOOLEAN NOT NULL DEFAULT false,
    email_recipients TEXT,                       -- comma-separated
    cooldown_hours  INT NOT NULL DEFAULT 24,     -- min hours between re-alerts for same condition
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (directory_id, rule_type)
);

-- Alert instances (fired alerts)
CREATE TABLE alert_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id         UUID NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    directory_id    UUID REFERENCES directory_connections(id) ON DELETE CASCADE,
    severity        VARCHAR(20) NOT NULL,
    title           VARCHAR(500) NOT NULL,
    detail          TEXT,
    context_key     VARCHAR(500),    -- dedup key (e.g. user DN, group DN, campaign ID)
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN, ACKNOWLEDGED, RESOLVED, DISMISSED
    acknowledged_by UUID REFERENCES accounts(id),
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_alert_instances_status ON alert_instances(status) WHERE status = 'OPEN';
CREATE INDEX idx_alert_instances_rule ON alert_instances(rule_id, context_key);
CREATE INDEX idx_alert_instances_dir ON alert_instances(directory_id, created_at DESC);
```

### 1b. Entities

- `AlertRule` — JPA entity with `@Type(JsonType.class)` for params JSONB
- `AlertInstance` — JPA entity with status enum

### 1c. Repositories

- `AlertRuleRepository` — `findAllByEnabledTrue()`, `findAllByDirectoryIdOrderByRuleTypeAsc()`
- `AlertInstanceRepository` — `findAllByStatusOrderByCreatedAtDesc()`, `existsByRuleIdAndContextKeyAndStatusIn()`, `countByDirectoryIdAndStatus()`

---

## Phase 2: Rule Engine

### 2a. `AlertRuleType` enum

Each value maps to a checker class. Organized by category:

```
// Separation of Duties
SOD_VIOLATION_NEW              — New SoD violation detected
SOD_VIOLATION_UNRESOLVED       — Violation open > N days
SOD_EXEMPTION_EXPIRING         — Exemption expires within N days

// Access Reviews
CAMPAIGN_DEADLINE_APPROACHING  — Campaign deadline within N days, < X% complete
CAMPAIGN_OVERDUE               — Campaign past deadline and still active
REVIEWER_INACTIVE              — Reviewer with pending decisions for > N days
USER_NOT_REVIEWED              — User not reviewed in N+ days

// Privileged Access
PRIVILEGED_GROUP_ADDITION      — User added to a configured high-privilege group
ADMIN_ACCOUNT_CREATED          — New admin account created
BULK_GROUP_ADDITION            — N+ users added to same group within X hours

// Account Lifecycle
DISABLED_ACCOUNT_IN_GROUPS     — Disabled account still has group memberships
DORMANT_ACCOUNT                — Account with no login/activity in N days
ORPHANED_ACCOUNT               — LDAP account not matched to HR record
ACCOUNT_POST_TERMINATION       — Account active after HR termination date

// Approvals
APPROVAL_STALE                 — Approval pending > N days
PROVISIONING_FAILURE           — Approved request failed to provision

// Directory Health
DIRECTORY_UNREACHABLE          — LDAP connection failed
INTEGRITY_VIOLATION            — Referential integrity issue detected
CHANGELOG_GAP                  — No audit events recorded in N hours
HIGH_CHANGE_VOLUME             — > N directory changes in X hours

// Compliance
SCHEDULED_REPORT_FAILURE       — Scheduled report job failed
AUDITOR_LINK_EXPIRING          — Auditor link expires within N days
```

### 2b. `AlertChecker` interface

```java
public interface AlertChecker {
    AlertRuleType ruleType();
    List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule);
}

public record AlertCandidate(
    String title,
    String detail,
    String contextKey  // dedup key
) {}
```

### 2c. Checker implementations (grouped by data source)

**Audit-event-based checkers** — query `AuditEventRepository` for recent events:
- `PrivilegedGroupAdditionChecker` — looks for `GROUP_MEMBER_ADD` events where group DN matches configured privileged groups
- `AdminAccountCreatedChecker` — looks for account creation events
- `BulkGroupAdditionChecker` — counts `GROUP_MEMBER_ADD` per group in time window
- `HighChangeVolumeChecker` — counts total events in time window

**Database-query-based checkers** — query existing repositories:
- `SodViolationNewChecker` — `SodViolationRepository` for OPEN violations not yet alerted
- `SodViolationUnresolvedChecker` — OPEN violations older than N days
- `CampaignDeadlineChecker` — `AccessReviewCampaignRepository` for approaching deadlines
- `CampaignOverdueChecker` — active campaigns past deadline
- `ApprovalStaleChecker` — `PendingApprovalRepository` for old PENDING records
- `ProvisioningFailureChecker` — approvals with `provisionError` set
- `ScheduledReportFailureChecker` — `ScheduledReportJobRepository` for FAILURE status
- `AuditorLinkExpiringChecker` — `AuditorLinkRepository` for links expiring within N days

**LDAP-query-based checkers** — query directory via existing LDAP services:
- `DisabledAccountInGroupsChecker` — find disabled users who are still group members
- `DormantAccountChecker` — users with `lastLogon` / `authTimestamp` older than N days

**HR-integration-based checkers:**
- `OrphanedAccountChecker` — LDAP accounts not matched to HR records
- `AccountPostTerminationChecker` — accounts active after HR termination date

### 2d. `AlertMonitoringService`

The orchestrator that runs all enabled checkers:

```java
@Service
public class AlertMonitoringService {

    @Scheduled(cron = "${ldapadmin.monitoring.cron:0 */15 * * * ?}")  // every 15 min
    public void evaluate() {
        List<AlertRule> rules = ruleRepo.findAllByEnabledTrue();
        for (AlertRule rule : rules) {
            AlertChecker checker = checkerRegistry.get(rule.getRuleType());
            List<AlertCandidate> candidates = checker.evaluate(directory, rule);
            for (AlertCandidate c : candidates) {
                if (!isDuplicate(rule, c)) {
                    createInstance(rule, c);
                    notify(rule, c);
                }
            }
        }
    }
}
```

**Deduplication**: Before creating an instance, check `existsByRuleIdAndContextKeyAndStatusIn(OPEN, ACKNOWLEDGED)`. Only create a new instance if no open/acknowledged instance exists for the same rule + context key, and the cooldown period has elapsed.

---

## Phase 3: Controller + DTOs

### 3a. `AlertController`

```
GET    /api/v1/superadmin/alerts                — list alert instances (paginated, filterable by status/severity/directory)
GET    /api/v1/superadmin/alerts/summary         — counts by status and severity
POST   /api/v1/superadmin/alerts/{id}/acknowledge — acknowledge an alert
POST   /api/v1/superadmin/alerts/{id}/dismiss     — dismiss (won't re-fire until next occurrence)
POST   /api/v1/superadmin/alerts/{id}/resolve     — mark resolved

GET    /api/v1/superadmin/alert-rules             — list all rules
PUT    /api/v1/superadmin/alert-rules/{id}        — update rule (enable/disable, thresholds, severity, notification settings)
POST   /api/v1/superadmin/alert-rules/initialize  — create default rules for a directory
```

### 3b. DTOs

- `AlertInstanceResponse` — id, ruleType, severity, title, detail, contextKey, status, createdAt, acknowledgedBy, acknowledgedAt
- `AlertSummaryResponse` — openCount, acknowledgedCount, criticalCount, highCount, mediumCount, lowCount
- `AlertRuleResponse` — id, directoryId, ruleType, enabled, severity, params, notifyInApp, notifyEmail, cooldownHours
- `UpdateAlertRuleRequest` — enabled, severity, params, notifyInApp, notifyEmail, emailRecipients, cooldownHours

---

## Phase 4: Frontend

### 4a. Alert Dashboard (`/superadmin/alerts`)

**Layout**: Full page with severity summary cards at top, filterable table below.

- **Summary cards**: 4 cards showing CRITICAL (red), HIGH (orange), MEDIUM (amber), LOW (gray) open alert counts
- **Filter bar**: Status (Open/Acknowledged/All), Severity, Directory, Rule Type
- **Alert table**: Severity icon, title, directory, rule type badge, age, status, action buttons (Acknowledge/Dismiss/Resolve)
- **Detail drawer**: Click an alert to see full detail text, context, and a link to the relevant page (e.g., SoD violations, campaign)
- **Auto-refresh**: Poll every 30 seconds

### 4b. Alert Rules Configuration (`/superadmin/alert-rules`)

**Layout**: Grouped by directory, with rule cards.

- **Per-directory section**: Directory name header, "Initialize Defaults" button for directories without rules
- **Rule cards**: Toggle on/off, severity dropdown, threshold inputs (rendered from params schema), notification toggles (in-app, email), cooldown hours
- **Bulk actions**: Enable All, Disable All per directory

### 4c. Integration points

- **Superadmin dashboard**: Add an "Active Alerts" card with critical/high counts and a link to `/superadmin/alerts`
- **Sidebar nav**: Add "Alerts" link under the Configure section with a badge showing open critical count
- **Notification bell**: Alert notifications appear alongside other notifications

---

## Phase 5: Default Rule Templates

When a directory is created or "Initialize Defaults" is clicked, create rules with sensible defaults:

| Rule Type | Default Severity | Default Params | Default Cooldown |
|-----------|-----------------|----------------|-----------------|
| SOD_VIOLATION_NEW | HIGH | — | 1h |
| SOD_VIOLATION_UNRESOLVED | MEDIUM | `{"days": 14}` | 24h |
| SOD_EXEMPTION_EXPIRING | MEDIUM | `{"days": 7}` | 24h |
| CAMPAIGN_DEADLINE_APPROACHING | HIGH | `{"days": 3, "minCompletionPct": 50}` | 24h |
| CAMPAIGN_OVERDUE | CRITICAL | — | 24h |
| REVIEWER_INACTIVE | MEDIUM | `{"days": 5}` | 48h |
| USER_NOT_REVIEWED | LOW | `{"days": 90}` | 168h |
| PRIVILEGED_GROUP_ADDITION | CRITICAL | `{"groups": []}` | 1h |
| ADMIN_ACCOUNT_CREATED | HIGH | — | 1h |
| BULK_GROUP_ADDITION | HIGH | `{"threshold": 10, "windowHours": 1}` | 4h |
| DISABLED_ACCOUNT_IN_GROUPS | MEDIUM | — | 24h |
| DORMANT_ACCOUNT | LOW | `{"days": 90}` | 168h |
| APPROVAL_STALE | MEDIUM | `{"days": 7}` | 48h |
| PROVISIONING_FAILURE | HIGH | — | 1h |
| DIRECTORY_UNREACHABLE | CRITICAL | — | 1h |
| CHANGELOG_GAP | HIGH | `{"hours": 6}` | 4h |
| HIGH_CHANGE_VOLUME | HIGH | `{"threshold": 100, "windowHours": 1}` | 4h |
| SCHEDULED_REPORT_FAILURE | MEDIUM | — | 24h |

Rules that require LDAP queries (DISABLED_ACCOUNT_IN_GROUPS, DORMANT_ACCOUNT) default to disabled and run on a longer cycle.

---

## Security

| Concern | Mitigation |
|---------|------------|
| Authorization | All endpoints require `SUPERADMIN` role |
| Read-only monitoring | Checkers never write to LDAP |
| Performance | 15-min evaluation cycle; LDAP-based checkers rate-limited; DB checkers use indexed queries |
| Alert spam | Cooldown periods + deduplication by (rule, contextKey) |
| Email disclosure | Email recipients configured per-rule; sensitive data (DNs, usernames) kept in detail, not subject |

---

## Implementation Order

1. **Phase 1** — Migration + entities + repositories (data model)
2. **Phase 2a-2b** — AlertRuleType enum + AlertChecker interface + AlertMonitoringService orchestrator
3. **Phase 2c** — Implement 8-10 highest-value checkers (SOD, campaign, privileged access, directory health)
4. **Phase 3** — Controller + DTOs + default rule initialization
5. **Phase 4a** — Alert dashboard frontend
6. **Phase 4b** — Alert rules configuration frontend
7. **Phase 4c** — Dashboard integration + sidebar badge
8. **Phase 2c continued** — Implement remaining checkers (lifecycle, HR, approval)
9. **Phase 5** — Auto-initialize defaults on directory creation

---

## File Summary

### New files

| # | File | Purpose |
|---|------|---------|
| 1 | `V57__alert_rules_and_instances.sql` | Migration |
| 2 | `AlertRule.java` | Entity |
| 3 | `AlertInstance.java` | Entity |
| 4 | `AlertRuleType.java` | Enum of all rule types |
| 5 | `AlertRuleRepository.java` | Repository |
| 6 | `AlertInstanceRepository.java` | Repository |
| 7 | `AlertChecker.java` | Checker interface |
| 8 | `AlertMonitoringService.java` | Orchestrator + scheduler |
| 9 | `AlertController.java` | REST endpoints |
| 10 | ~15 checker implementation classes | One per rule type |
| 11 | DTOs (4 files) | Request/response records |
| 12 | `frontend/src/api/alerts.js` | API client |
| 13 | `AlertDashboardView.vue` | Alert dashboard |
| 14 | `AlertRulesView.vue` | Rule configuration |

### Modified files

| File | Change |
|------|--------|
| `router/index.js` | Add alert routes |
| `AppLayout.vue` | Add Alerts nav link with badge |
| `DashboardView.vue` | Add active alerts card |
| `application.yml` | Add monitoring cron config |

### Reused existing services (no changes)

| Service | Usage |
|---------|-------|
| `NotificationService` | In-app alert notifications |
| `ApprovalNotificationService` | Email delivery |
| `AuditQueryService` | Event-based checkers |
| `SodViolationRepository` | SoD checkers |
| `AccessReviewCampaignRepository` | Campaign checkers |
| `PendingApprovalRepository` | Approval checkers |
| `LdapUserService` / `LdapGroupService` | LDAP-based checkers |
