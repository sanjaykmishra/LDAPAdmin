# Workday Integration Analysis for LDAPAdmin

## Current State

The HR sync subsystem already has a working BambooHR adapter with a clean architecture: per-directory `HrConnection` entity, a `BambooHrClient` adapter, cron-based scheduling, identity matching against LDAP, orphaned account detection, and full audit trail. The provider model is enum-based (`HrProvider`) — currently only `BAMBOOHR`.

---

## Business Value

**High value.** Workday is the dominant enterprise HRIS — ~60% of Fortune 500. The current BambooHR adapter targets SMB. Adding Workday unlocks the enterprise segment where LDAPAdmin's access review, compliance, and provisioning features matter most.

Key value drivers:

1. **Joiner/Mover/Leaver automation** — Workday is the system of record for employment status. Auto-detecting terminations and flagging orphaned LDAP accounts is the #1 use case enterprises ask for.

2. **Access review enrichment** — HR data (department, manager, title, hire date) enriches access review campaigns with business context that pure LDAP data lacks.

3. **Compliance** — SOX, SOC 2, and ISO 27001 audits require demonstrating that access is revoked promptly on termination. The `orphanedAccounts` feature becomes audit evidence.

4. **Competitive positioning** — Most LDAP management tools don't have native HR sync. This differentiates LDAPAdmin from phpLDAPadmin, Apache Directory Studio, etc.

---

## Technical Approach

### Workday API Options

| API | Auth | Best For | Complexity |
|-----|------|----------|------------|
| **Workday RaaS (Reports-as-a-Service)** | Basic Auth or OAuth 2.0 | Read-only employee directory | Low — customer builds a custom report, we consume its JSON/CSV endpoint |
| **Workday REST API (Workers)** | OAuth 2.0 (client_credentials) | Full worker lifecycle data | Medium — paginated, requires tenant-specific URL |
| **Workday SOAP (HCM Web Services)** | WS-Security or Basic Auth | Legacy integrations | High — WSDL parsing, XML handling, version-specific schemas |

**Recommendation: Support both RaaS (primary) and REST API (secondary).**

RaaS is the path of least resistance — Workday admins routinely create custom reports and expose them as REST endpoints. It avoids the complexity of Workday's full API and lets customers control exactly which fields are exposed. The REST Workers API covers customers who want a zero-config experience.

### Architecture Changes

#### 1. New enum value + client class (minimal footprint)

```
HrProvider.WORKDAY   (new enum value)
WorkdayClient.java   (new adapter, same pattern as BambooHrClient)
```

#### 2. HrConnection entity additions

The existing schema needs a few Workday-specific fields:

| Field | Purpose |
|-------|---------|
| `tenantAlias` | Workday tenant (e.g., `acme_prod`) — replaces `subdomain` |
| `raasReportUrl` | Full URL to custom RaaS report endpoint |
| `authMode` | `BASIC_AUTH` or `OAUTH2` |
| `oauthTokenUrl` | Token endpoint for OAuth flow |
| `clientIdEncrypted` | OAuth client ID (encrypted) |
| `clientSecretEncrypted` | OAuth client secret (encrypted) |
| `refreshTokenEncrypted` | OAuth refresh token (encrypted, optional) |

These can be nullable columns on `hr_connections` (new Flyway migration), keeping the single-table design that BambooHR uses.

#### 3. WorkdayClient implementation

```java
public class WorkdayClient {
    // RaaS mode: GET {raasReportUrl}?format=json
    // REST mode: GET https://wd2-impl-services1.workday.com/ccx/api/v1/{tenant}/workers

    List<Map<String, String>> fetchAllEmployees(HrConnection conn, String decryptedKey);
    int testConnection(HrConnection conn, String decryptedKey);
}
```

Key considerations:
- **Pagination**: Workday REST API uses `offset`/`limit` with a default page size of 100. RaaS returns all rows in one response (for typical report sizes).
- **Rate limiting**: Workday enforces per-tenant rate limits. Use exponential backoff + respect `Retry-After` headers.
- **Field mapping**: RaaS reports have customer-defined column names. We need a configurable field mapping (stored as JSON in `hr_connections` or a separate mapping table) rather than hardcoded field names.
- **OAuth token refresh**: Store the refresh token, auto-refresh access tokens before expiry. Token lifetime is typically 14 minutes.

#### 4. Field mapping flexibility

BambooHR has fixed field names (`workEmail`, `firstName`, etc.). Workday RaaS reports have arbitrary column names. Add a `fieldMappings` JSON column:

```json
{
  "employeeId": "Employee_ID",
  "firstName": "Legal_First_Name",
  "lastName": "Legal_Last_Name",
  "workEmail": "Email_Address",
  "department": "Cost_Center",
  "status": "Employee_Status",
  "hireDate": "Hire_Date",
  "terminationDate": "Termination_Date",
  "supervisorEmail": "Manager_Email"
}
```

This also benefits future integrations (SAP SuccessFactors, UKG, etc.).

#### 5. Sync service changes

`HrSyncService.sync()` currently calls `BambooHrClient` directly. Refactor to a provider pattern:

```java
public interface HrProviderClient {
    List<Map<String, String>> fetchAllEmployees(HrConnection conn, String decryptedKey);
    int testConnection(HrConnection conn, String decryptedKey);
}
```

Then in `HrSyncService`:
```java
HrProviderClient client = switch (conn.getProvider()) {
    case BAMBOOHR -> bambooHrClient;
    case WORKDAY  -> workdayClient;
};
```

The rest of the sync pipeline (matching, persistence, audit) remains unchanged.

### Migration Plan

```sql
-- V46__workday_integration.sql
ALTER TABLE hr_connections ADD COLUMN tenant_alias VARCHAR(255);
ALTER TABLE hr_connections ADD COLUMN raas_report_url VARCHAR(1024);
ALTER TABLE hr_connections ADD COLUMN auth_mode VARCHAR(20) DEFAULT 'BASIC_AUTH';
ALTER TABLE hr_connections ADD COLUMN oauth_token_url VARCHAR(1024);
ALTER TABLE hr_connections ADD COLUMN client_id_encrypted VARCHAR(1024);
ALTER TABLE hr_connections ADD COLUMN client_secret_encrypted VARCHAR(1024);
ALTER TABLE hr_connections ADD COLUMN refresh_token_encrypted VARCHAR(1024);
ALTER TABLE hr_connections ADD COLUMN field_mappings JSONB;

-- Unique constraint already on (directory_id, provider) — no change needed
```

### Frontend Changes

- **HrConnectionView.vue**: Add provider selector (BambooHR / Workday). Show provider-specific fields conditionally:
  - BambooHR: subdomain + API key (existing)
  - Workday: tenant alias, RaaS URL or REST mode toggle, auth mode (Basic/OAuth), credentials, field mapping editor
- **Field mapping UI**: Simple key-value editor for RaaS mode (map "our field" → "your report column")

### Effort Estimate

| Component | Scope |
|-----------|-------|
| `WorkdayClient` + OAuth token management | New class (~200 lines) |
| `HrProviderClient` interface + refactor | Extract interface, minimal change to `HrSyncService` |
| Flyway migration | 1 new migration file |
| Entity/DTO updates | Add nullable fields to `HrConnection`, update DTOs |
| Frontend provider toggle + Workday form | Conditional sections in existing view |
| Field mapping (backend + UI) | JSON column + simple key-value editor |
| Tests | Mirror `BambooHrClientTest` pattern for Workday |

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| **Workday tenant setup is complex** — customers need to create an Integration System User (ISU) and security groups | Provide step-by-step setup guide in docs; RaaS mode reduces config burden |
| **RaaS column names vary per customer** | Configurable field mapping (not hardcoded) |
| **OAuth token refresh failures** | Store refresh token, retry with backoff, alert on repeated failure via existing audit trail |
| **Large employee populations (50k+)** | Existing `max-employees-per-sync` safety limit applies; REST API pagination handles memory |
| **Workday sandbox vs. production URLs differ** | Let customer provide full tenant URL rather than constructing it |

---

## Recommendation

Start with **RaaS-only support** — it covers 80% of use cases with 40% of the effort. The REST Workers API can follow as a v2 enhancement. The key architectural investment is the `HrProviderClient` interface and configurable field mappings, which pay off for any future HRIS integration (SAP SuccessFactors, UKG Pro, ADP).
