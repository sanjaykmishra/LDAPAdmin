# Feature 3.1: HR System Integration (BambooHR) — Implementation Plan

## Overview

Read-only sync from BambooHR: employee list, status changes, department/role changes. Maps employee → LDAP DN by email or employee ID. Enables joiner/mover/leaver detection and orphaned account identification.

**Estimated effort:** 2-3 weeks
**Dependencies:** None (Feature 3.2 orphaned account dashboard depends on *this*)

---

## Architecture

```
BambooHR API ──────► BambooHrClient ──────► BambooHrSyncService
                         │                         │
                         │                    ┌────┴────┐
                         │                    ▼         ▼
                    HrEmployee          HrSyncRun    AuditService
                    (entity)            (entity)
                         │
                         ▼
                  Identity Matching
                  (email / employeeId → LDAP DN)
                         │
                    ┌────┴────────────┐
                    ▼                 ▼
              OrphanedAccount    HrStatusChange
              detection          event logging
```

The integration is **read-only** — it never writes to BambooHR. It reads employee data and cross-references against LDAP to surface gaps.

---

## Step 1: Database Migrations

### V44__hr_integration.sql

**Tables:**

```sql
-- HR connector configuration (per directory)
CREATE TABLE hr_connections (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id      UUID NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    provider          VARCHAR(50) NOT NULL DEFAULT 'BAMBOOHR',  -- extensible for future HR systems
    display_name      VARCHAR(200) NOT NULL,
    enabled           BOOLEAN NOT NULL DEFAULT false,

    -- BambooHR-specific config
    subdomain         VARCHAR(200),           -- e.g. "acme" for acme.bamboohr.com
    api_key_encrypted TEXT,                   -- AES-256 GCM encrypted

    -- Identity matching config
    match_attribute   VARCHAR(100) NOT NULL DEFAULT 'mail',  -- LDAP attribute to match against
    match_field       VARCHAR(100) NOT NULL DEFAULT 'workEmail',  -- HR field to match against

    -- Sync schedule
    sync_cron         VARCHAR(50) NOT NULL DEFAULT '0 0 * * * ?',  -- hourly
    last_sync_at      TIMESTAMPTZ,
    last_sync_status  VARCHAR(20),             -- SUCCESS, FAILED, PARTIAL
    last_sync_message TEXT,
    last_sync_employee_count INTEGER,

    created_by        UUID REFERENCES accounts(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(directory_id, provider)
);

CREATE INDEX idx_hr_connections_directory ON hr_connections(directory_id);

-- Cached HR employee records (refreshed each sync)
CREATE TABLE hr_employees (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hr_connection_id  UUID NOT NULL REFERENCES hr_connections(id) ON DELETE CASCADE,
    employee_id       VARCHAR(100) NOT NULL,   -- BambooHR employee ID
    work_email        VARCHAR(500),
    first_name        VARCHAR(200),
    last_name         VARCHAR(200),
    display_name      VARCHAR(500),
    department        VARCHAR(200),
    job_title         VARCHAR(200),
    status            VARCHAR(50) NOT NULL,     -- ACTIVE, TERMINATED, ON_LEAVE, etc.
    hire_date         DATE,
    termination_date  DATE,
    supervisor_id     VARCHAR(100),
    supervisor_email  VARCHAR(500),

    -- Identity matching result
    matched_ldap_dn   VARCHAR(1000),           -- NULL if no match found
    match_confidence  VARCHAR(20),             -- EXACT, FUZZY, NONE

    last_synced_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(hr_connection_id, employee_id)
);

CREATE INDEX idx_hr_employees_connection ON hr_employees(hr_connection_id);
CREATE INDEX idx_hr_employees_status ON hr_employees(hr_connection_id, status);
CREATE INDEX idx_hr_employees_matched ON hr_employees(hr_connection_id, matched_ldap_dn);

-- Sync run history (audit trail)
CREATE TABLE hr_sync_runs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hr_connection_id  UUID NOT NULL REFERENCES hr_connections(id) ON DELETE CASCADE,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL DEFAULT 'RUNNING',  -- RUNNING, SUCCESS, FAILED
    total_employees   INTEGER,
    new_employees     INTEGER DEFAULT 0,
    updated_employees INTEGER DEFAULT 0,
    terminated_count  INTEGER DEFAULT 0,
    matched_count     INTEGER DEFAULT 0,
    unmatched_count   INTEGER DEFAULT 0,
    orphaned_count    INTEGER DEFAULT 0,          -- terminated but still has LDAP access
    error_message     TEXT,
    triggered_by      VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED'  -- SCHEDULED, MANUAL, WEBHOOK
);

CREATE INDEX idx_hr_sync_runs_connection ON hr_sync_runs(hr_connection_id, started_at DESC);
```

---

## Step 2: Entities & Enums

**New package:** `src/main/java/com/ldapadmin/entity/hr/`

| File | Purpose |
|------|---------|
| `HrConnection.java` | JPA entity for `hr_connections` — config per directory |
| `HrEmployee.java` | JPA entity for `hr_employees` — cached employee data |
| `HrSyncRun.java` | JPA entity for `hr_sync_runs` — sync execution history |

**New enums** in `entity/enums/`:

| Enum | Values |
|------|--------|
| `HrProvider` | `BAMBOOHR` (extensible for Workday, Personio later in Phase 4) |
| `HrEmployeeStatus` | `ACTIVE, TERMINATED, ON_LEAVE, INACTIVE` |
| `HrSyncStatus` | `RUNNING, SUCCESS, FAILED` |
| `HrSyncTrigger` | `SCHEDULED, MANUAL, WEBHOOK` |
| `HrMatchConfidence` | `EXACT, FUZZY, NONE` |

**Modify `AuditAction` enum** — add:

```java
// ── HR integration ──────────────────────────────────────────────────
HR_SYNC_STARTED      ("hr.sync_started"),
HR_SYNC_COMPLETED    ("hr.sync_completed"),
HR_SYNC_FAILED       ("hr.sync_failed"),
HR_EMPLOYEE_MATCHED  ("hr.employee_matched"),
HR_ORPHAN_DETECTED   ("hr.orphan_detected"),
```

**Modify `FeatureKey` enum** — add:

```java
HR_MANAGE            ("hr.manage"),
HR_VIEW              ("hr.view"),
```

---

## Step 3: Repositories

**New package:** `src/main/java/com/ldapadmin/repository/hr/`

| File | Key Queries |
|------|-------------|
| `HrConnectionRepository.java` | `findByDirectoryId`, `findByEnabledTrue` |
| `HrEmployeeRepository.java` | `findByHrConnectionId`, `findByHrConnectionIdAndStatus`, `findByHrConnectionIdAndMatchedLdapDnIsNull`, `findByHrConnectionIdAndStatusAndMatchedLdapDnIsNotNull` (orphaned = terminated + has LDAP match), `countByHrConnectionIdAndStatus`, `deleteByHrConnectionId` |
| `HrSyncRunRepository.java` | `findByHrConnectionIdOrderByStartedAtDesc` (paginated), `findTopByHrConnectionIdOrderByStartedAtDesc` |

---

## Step 4: DTOs

**New package:** `src/main/java/com/ldapadmin/dto/hr/`

| DTO | Purpose |
|-----|---------|
| `HrConnectionDto` | Response for connection config (masks API key) |
| `CreateHrConnectionRequest` | Create connection (subdomain, apiKey, matchAttribute, matchField, syncCron) |
| `UpdateHrConnectionRequest` | Update connection config |
| `HrEmployeeDto` | Employee record with match status |
| `HrSyncRunDto` | Sync run summary |
| `HrSyncSummaryDto` | Dashboard widget data: total employees, matched, unmatched, orphaned counts |
| `HrOrphanedAccountDto` | Terminated employee + their active LDAP DN + group memberships |
| `HrTestConnectionResponse` | Result of API key test (success/fail, employee count) |

---

## Step 5: BambooHR API Client

**New file:** `src/main/java/com/ldapadmin/service/hr/BambooHrClient.java`

Follows the `SiemClient` pattern — uses `java.net.http.HttpClient`.

**BambooHR API endpoints used:**

1. `GET /api/gateway.php/{subdomain}/v1/employees/directory` — full employee directory
2. `GET /api/gateway.php/{subdomain}/v1/employees/{id}?fields=...` — single employee detail
3. `GET /api/gateway.php/{subdomain}/v1/employees/changed?since=...` — delta changes since last sync

**Auth:** Basic auth with API key as password, `x:` as username prefix.

**Methods:**

```java
List<BambooEmployee> fetchAllEmployees(String subdomain, String apiKey)
List<BambooEmployee> fetchChangedSince(String subdomain, String apiKey, OffsetDateTime since)
BambooEmployee fetchEmployee(String subdomain, String apiKey, String employeeId)
boolean testConnection(String subdomain, String apiKey)  // returns true if API responds
```

**Rate limiting:** BambooHR allows 100 requests/15 min. The full directory endpoint returns all employees in one call, so normal sync uses 1 request. Delta sync for large orgs may need pagination handling.

---

## Step 6: Sync Service

**New file:** `src/main/java/com/ldapadmin/service/hr/HrSyncService.java`

**Core method: `sync(HrConnection connection, HrSyncTrigger trigger)`**

Flow:

1. Create `HrSyncRun` record (status=RUNNING)
2. Fetch employees from BambooHR (full or delta based on `lastSyncAt`)
3. Upsert into `hr_employees` table (by employee_id)
4. Run identity matching: for each employee, search LDAP for `(matchAttribute=employeeValue)`
5. Update `matched_ldap_dn` and `match_confidence` on each `HrEmployee`
6. Detect orphaned accounts: `status=TERMINATED AND matched_ldap_dn IS NOT NULL` → check if DN still exists and is enabled in LDAP
7. Update `HrSyncRun` with counts and status
8. Record audit events
9. Update `HrConnection.lastSyncAt`

**Identity matching logic:**

```java
private String matchEmployeeToLdap(DirectoryConnection dir, HrEmployee employee, HrConnection config) {
    String searchValue = getFieldValue(employee, config.getMatchField());  // e.g. workEmail
    String ldapAttr = config.getMatchAttribute();  // e.g. "mail"
    String filter = "(" + ldapAttr + "=" + escapeLdap(searchValue) + ")";

    List<LdapUser> results = ldapUserService.search(dir, filter, 2, "dn");
    if (results.size() == 1) return results.get(0).getDn();  // EXACT
    if (results.size() > 1) log warning, return null;         // AMBIGUOUS
    return null;                                               // NO MATCH
}
```

---

## Step 7: Scheduler

**New file:** `src/main/java/com/ldapadmin/service/hr/HrSyncScheduler.java`

```java
@Scheduled(fixedDelayString = "${ldapadmin.hr.poll-interval-ms:60000}")
public void pollHrConnections() {
    // For each enabled HrConnection:
    //   Parse cron expression, check if due
    //   If due, call hrSyncService.sync(connection, SCHEDULED)
}
```

Uses Spring's `CronExpression` to evaluate whether each connection's cron is due. Simpler than full Quartz — matches the existing `AccessReviewScheduler` pattern.

---

## Step 8: Controller

**New file:** `src/main/java/com/ldapadmin/controller/directory/HrConnectionController.java`

**Base path:** `/api/v1/directories/{directoryId}/hr`

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| `GET` | `/` | `HR_VIEW` | Get HR connection config for this directory |
| `POST` | `/` | `HR_MANAGE` | Create HR connection |
| `PUT` | `/` | `HR_MANAGE` | Update HR connection |
| `DELETE` | `/` | `HR_MANAGE` | Remove HR connection |
| `POST` | `/test` | `HR_MANAGE` | Test BambooHR API connectivity |
| `POST` | `/sync` | `HR_MANAGE` | Trigger manual sync |
| `GET` | `/sync-history` | `HR_VIEW` | List sync run history (paginated) |
| `GET` | `/employees` | `HR_VIEW` | List HR employees (with filters: status, matched/unmatched) |
| `GET` | `/employees/orphaned` | `HR_VIEW` | List orphaned accounts (terminated + active LDAP) |
| `GET` | `/summary` | `HR_VIEW` | Summary counts for dashboard widget |

---

## Step 9: Frontend

### API Client

**New file:** `frontend/src/api/hrIntegration.js`

All CRUD + sync trigger + employee listing + orphaned account listing endpoints.

### Views

**New file:** `frontend/src/views/hr/HrConnectionView.vue`

Sections:

1. **Connection Setup** — form: subdomain, API key (masked), match attribute selector (mail/uid/employeeNumber), match field selector (workEmail/homeEmail/employeeId), sync cron, enable/disable toggle
2. **Test Connection** button — calls `/test`, shows employee count on success
3. **Sync Now** button — triggers manual sync, shows progress
4. **Last Sync Status** — timestamp, counts (total/matched/unmatched/orphaned), errors
5. **Sync History** — table of past sync runs

**New file:** `frontend/src/views/hr/HrEmployeesView.vue`

- Tab filters: All / Active / Terminated / Orphaned
- Table columns: Name, Email, Department, Status, Hire Date, Termination Date, Matched LDAP DN, Match Confidence
- Orphaned tab highlights terminated employees with active LDAP accounts
- Click row to show employee detail with their LDAP group memberships

### Router

Add to `frontend/src/router/index.js`:

```javascript
{
  path: 'directories/:dirId/hr',
  name: 'hrConnection',
  component: () => import('@/views/hr/HrConnectionView.vue'),
},
{
  path: 'directories/:dirId/hr/employees',
  name: 'hrEmployees',
  component: () => import('@/views/hr/HrEmployeesView.vue'),
},
```

### Navigation

Add "HR Integration" item to the directory sidebar nav (between Compliance Reports and Campaign Templates).

---

## Step 10: Feature Permission Migration

### V45__hr_feature_permissions.sql

```sql
INSERT INTO feature_permission_defaults (feature_key, role, enabled)
VALUES
    ('hr.manage', 'SUPERADMIN', true),
    ('hr.manage', 'ADMIN', false),
    ('hr.view', 'SUPERADMIN', true),
    ('hr.view', 'ADMIN', true);
```

---

## Step 11: Tests

### Service Tests

| Test File | Coverage |
|-----------|----------|
| `BambooHrClientTest.java` | API response parsing, error handling, auth header construction, rate limit detection |
| `HrSyncServiceTest.java` | Full sync flow, delta sync, identity matching (exact/ambiguous/none), orphan detection, status transitions, audit event recording, error handling |

### Controller Tests

| Test File | Coverage |
|-----------|----------|
| `HrConnectionControllerTest.java` | CRUD operations, auth (superadmin/admin/unauth), test connectivity, trigger sync, list employees, orphaned filter, summary endpoint |

**Target:** ~15 service tests + ~12 controller tests

---

## Step 12: Configuration

**Add to `application.yml`:**

```yaml
ldapadmin:
  hr:
    poll-interval-ms: ${HR_POLL_INTERVAL_MS:60000}  # scheduler polling interval
    sync-timeout-minutes: ${HR_SYNC_TIMEOUT_MINUTES:30}
    max-employees-per-sync: ${HR_MAX_EMPLOYEES:50000}
```

---

## File Summary

### New Files (22)

| Layer | Files |
|-------|-------|
| **Migration** | `V44__hr_integration.sql`, `V45__hr_feature_permissions.sql` |
| **Entities** | `HrConnection.java`, `HrEmployee.java`, `HrSyncRun.java` |
| **Enums** | `HrProvider.java`, `HrEmployeeStatus.java`, `HrSyncStatus.java`, `HrSyncTrigger.java`, `HrMatchConfidence.java` |
| **Repositories** | `HrConnectionRepository.java`, `HrEmployeeRepository.java`, `HrSyncRunRepository.java` |
| **DTOs** | `HrConnectionDto.java`, `CreateHrConnectionRequest.java`, `UpdateHrConnectionRequest.java`, `HrEmployeeDto.java`, `HrSyncRunDto.java`, `HrSyncSummaryDto.java`, `HrOrphanedAccountDto.java`, `HrTestConnectionResponse.java` |
| **Services** | `BambooHrClient.java`, `HrSyncService.java`, `HrSyncScheduler.java` |
| **Controller** | `HrConnectionController.java` |
| **Frontend** | `hrIntegration.js`, `HrConnectionView.vue`, `HrEmployeesView.vue` |
| **Tests** | `BambooHrClientTest.java`, `HrSyncServiceTest.java`, `HrConnectionControllerTest.java` |

### Modified Files (5)

| File | Change |
|------|--------|
| `AuditAction.java` | Add 5 HR audit actions |
| `FeatureKey.java` | Add `HR_MANAGE`, `HR_VIEW` |
| `application.yml` | Add `ldapadmin.hr.*` config block |
| `frontend/src/router/index.js` | Add HR routes |
| Sidebar navigation component | Add HR nav item |

---

## Build Order

| Week | Tasks | Deliverable |
|------|-------|-------------|
| **Week 1** | Migrations, entities, enums, repositories, DTOs, BambooHrClient | Data layer + API client |
| **Week 2** | HrSyncService, HrSyncScheduler, controller, service tests, controller tests | Working backend with full sync |
| **Week 3** | Frontend views, router, nav, integration testing, documentation | Complete feature |

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| BambooHR rate limit (100 req/15 min) | Full directory endpoint returns all employees in 1 call; delta sync after first full sync |
| Large employee counts (10k+) | Batch upserts, configurable `max-employees-per-sync` limit |
| LDAP identity matching ambiguity | Log ambiguous matches, surface in UI, allow manual override in future iteration |
| API key rotation | Store encrypted in DB (not env var), updateable via settings UI |
| BambooHR API changes | Isolate in `BambooHrClient` — single class to update |
