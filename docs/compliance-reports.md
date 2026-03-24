# Compliance Reports (PDF) — Manual Testing Guide

## Overview

Three PDF compliance report types are available:

| Report | Endpoint | Auth | Scope |
|--------|----------|------|-------|
| User Access Report | `GET /api/v1/directories/{dirId}/compliance-reports/user-access` | `@RequiresFeature(REPORTS_RUN)` | Directory |
| Access Review Summary | `GET /api/v1/directories/{dirId}/compliance-reports/access-review-summary/{campaignId}` | `@RequiresFeature(REPORTS_RUN)` | Directory |
| Privileged Account Inventory | `GET /api/v1/compliance-reports/privileged-accounts` | `@PreAuthorize(SUPERADMIN)` | Global |

## Frontend Access

1. Log in as admin or superadmin
2. Select a profile (admin) or use the superadmin dashboard
3. Navigate to **Compliance Reports** in the sidebar (below Reports)
4. Choose a report, fill in parameters, and click **Download PDF**

## Manual API Testing (curl)

### Prerequisites

Obtain a JWT token by logging in:

```bash
TOKEN=$(curl -s -c cookies.txt http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"superadmin","password":"YOUR_PASSWORD"}' | jq -r '.token // empty')
```

> If using httpOnly cookies, pass `-b cookies.txt` instead of the Authorization header.

### 1. User Access Report

All groups in a directory:

```bash
curl -b cookies.txt -o user-access-report.pdf \
  http://localhost:8080/api/v1/directories/{DIRECTORY_ID}/compliance-reports/user-access
```

Filtered by a specific group:

```bash
curl -b cookies.txt -o user-access-filtered.pdf \
  "http://localhost:8080/api/v1/directories/{DIRECTORY_ID}/compliance-reports/user-access?groupDn=cn%3Dadmins%2Cou%3Dgroups%2Cdc%3Dexample%2Cdc%3Dcom"
```

**What to verify:**
- PDF opens correctly in a viewer
- Header shows app name, report title, and generation timestamp
- Table lists Group DN, Group Name, and Member columns
- Groups with no members show "(no members)"
- Filtered report shows only the specified group

### 2. Access Review Summary

```bash
curl -b cookies.txt -o access-review-summary.pdf \
  http://localhost:8080/api/v1/directories/{DIRECTORY_ID}/compliance-reports/access-review-summary/{CAMPAIGN_ID}
```

**What to verify:**
- "Campaign Details" section shows name, status, dates, confirm/revoke/pending counts
- "Decision Details" table shows each member decision with group, reviewer, and comment
- Pending decisions show "PENDING" with no decided-at date

### 3. Privileged Account Inventory (Superadmin only)

```bash
curl -b cookies.txt -o privileged-accounts.pdf \
  http://localhost:8080/api/v1/compliance-reports/privileged-accounts
```

**What to verify:**
- All superadmin and admin accounts are listed
- Columns: Username, Display Name, Role, Auth Type, Active, Last Login, Profile Access, Feature Overrides
- Feature overrides show dot-notation keys (e.g. `bulk.import=OFF`)
- Non-superadmin users get 403 Forbidden

### 4. PDF via Existing Report Runner

The existing on-demand report endpoint now supports PDF:

```bash
curl -b cookies.txt -o report.pdf \
  http://localhost:8080/api/v1/directories/{DIRECTORY_ID}/reports/run \
  -H 'Content-Type: application/json' \
  -d '{"reportType":"USERS_IN_GROUP","reportParams":{"groupDn":"cn=admins,dc=example,dc=com"},"outputFormat":"PDF"}'
```

**What to verify:**
- Response Content-Type is `application/pdf`
- Filename ends in `.pdf`
- Data matches the equivalent CSV report

## Authorization Checks

| Scenario | Expected |
|----------|----------|
| Unauthenticated request | 401 |
| Admin with `REPORTS_RUN` permission | 200 (user-access, review-summary) |
| Admin without `REPORTS_RUN` permission | 403 |
| Admin accessing privileged-accounts | 403 |
| Superadmin accessing privileged-accounts | 200 |

## Test Coverage

- `PdfReportServiceTest` — 14 unit tests covering all three reports, edge cases (empty data, null fields, group filtering), and PDF validity checks
- `ComplianceReportControllerTest` — 9 WebMvcTest tests covering auth (401/403/200), parameter passing, content-type, and content-disposition headers
- All 305 project tests pass with 0 failures
