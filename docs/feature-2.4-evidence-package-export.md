# Feature 2.4: Evidence Package Export

## Overview

One-click generation of a comprehensive ZIP evidence package for compliance audits. The package bundles PDF reports, campaign decisions, SoD data, approval history, and LDAP entitlement snapshots into a single tamper-evident archive.

## What Was Built

### Backend

| Component | File |
|---|---|
| EvidencePackageRequest DTO | `src/main/java/com/ldapadmin/dto/evidence/EvidencePackageRequest.java` |
| EvidencePackageService | `src/main/java/com/ldapadmin/service/EvidencePackageService.java` |
| EvidencePackageController | `src/main/java/com/ldapadmin/controller/directory/EvidencePackageController.java` |

### Frontend

| Component | File |
|---|---|
| ComplianceReportsView.vue (updated) | `frontend/src/views/reports/ComplianceReportsView.vue` |
| complianceReports.js API (updated) | `frontend/src/api/complianceReports.js` |

## API Endpoint

### `POST /api/v1/directories/{directoryId}/evidence-package`

**Authorization:** Requires `reports.run` feature permission.

**Request body:**
```json
{
  "campaignIds": ["uuid-1", "uuid-2"],
  "includeSod": true,
  "includeEntitlements": false
}
```

**Response:** `application/zip` with `Content-Disposition: attachment; filename="evidence-package-2026-03-24.zip"`

**Rate limit:** One concurrent generation per user. Returns `429 Too Many Requests` with `Retry-After: 30` if a generation is already in progress.

## ZIP Package Structure

```
evidence-package-2026-03-24.zip
├── manifest.json                              # Metadata, file list, SHA-256 checksums, HMAC signature
├── reports/
│   ├── user-access-report.pdf                 # All group memberships in the directory
│   └── privileged-account-inventory.pdf       # Admin/superadmin accounts and roles
├── campaigns/
│   └── {campaign-name}/
│       ├── access-review-summary.pdf          # Campaign decision breakdown
│       ├── decisions.csv                      # Raw decision data
│       └── history.json                       # Campaign status change history
├── sod/                                       # (optional, when includeSod=true)
│   ├── policies.json                          # All SoD policy definitions
│   └── violations.json                        # Current open violations
├── approval-history/
│   └── approvals.json                         # Approval workflow records
└── entitlements/                              # (optional, when includeEntitlements=true)
    └── user-entitlements.json                 # All users and their group memberships
```

## Manifest Format

```json
{
  "generatedAt": "2026-03-24T15:30:00Z",
  "generatedBy": "admin",
  "directoryId": "...",
  "directoryName": "Corporate AD",
  "options": {
    "campaignIds": ["..."],
    "includeSod": true,
    "includeEntitlements": false
  },
  "files": [
    {
      "path": "reports/user-access-report.pdf",
      "sha256": "a1b2c3...",
      "size": "45231"
    }
  ],
  "hmacSha256Signature": "d4e5f6..."
}
```

### Tamper Evidence

- Each file in the package has a SHA-256 checksum in the manifest
- The manifest (excluding the signature field) is signed with HMAC-SHA256 using the application's `ENCRYPTION_KEY`
- To verify: remove the `hmacSha256Signature` field, re-serialize the JSON, compute HMAC-SHA256 with the same key, and compare

## Graceful Degradation

If any component fails during generation (e.g., LDAP is unreachable for user access report), the package is still generated with the remaining files. Failures are logged as warnings. The manifest's file list reflects only the files that were successfully generated.

## Frontend UI

The Evidence Package section is added below the existing PDF report cards in `ComplianceReportsView.vue`:

- **Campaign multi-select** — checkboxes for all available campaigns
- **Include SoD** toggle — defaults to on
- **Include Entitlements** toggle — defaults to off
- **Download button** — green, with loading spinner during generation
- **Success toast** — shows file size after download
- **Rate limit handling** — friendly message when 429 is returned

## Manual Testing Guide

### Prerequisites

1. Running LDAPAdmin instance with at least one connected LDAP directory
2. Admin account with `reports.run` feature permission
3. For full testing: at least one access review campaign, some SoD policies (Feature 2.1), and some pending approvals

### Test 1: Basic Evidence Package (No Campaigns)

1. Navigate to **Compliance Reports** page for a directory
2. In the **Evidence Package** section, check at least one campaign
3. Leave SoD toggle on, Entitlements toggle off
4. Click **Download Evidence Package (ZIP)**
5. **Expected:** ZIP downloads with `evidence-package-{date}.zip` filename
6. Extract the ZIP and verify:
   - `manifest.json` exists with `generatedAt`, `generatedBy`, file list
   - `reports/user-access-report.pdf` exists (may be small if directory has no groups)
   - `reports/privileged-account-inventory.pdf` exists
   - `approval-history/approvals.json` exists

### Test 2: Evidence Package with Campaigns

1. Select one or more campaigns in the checkbox list
2. Download the package
3. **Expected:** For each selected campaign, verify:
   - `campaigns/{name}/access-review-summary.pdf` exists
   - `campaigns/{name}/decisions.csv` exists with proper columns
   - `campaigns/{name}/history.json` exists with status change records

### Test 3: SoD Data Inclusion

1. Ensure SoD policies exist for the directory (Feature 2.1)
2. Enable the **Include SoD Policies & Violations** toggle
3. Download the package
4. **Expected:**
   - `sod/policies.json` contains policy definitions with name, groups, severity, action
   - `sod/violations.json` contains open violations with user DN and detection timestamp

### Test 4: Entitlements Snapshot

1. Enable the **Include Entitlement Snapshot** toggle
2. Download the package
3. **Expected:**
   - `entitlements/user-entitlements.json` contains an array of users
   - Each user entry has `dn`, `cn`, `loginName`, `displayName`, `mail`, `groups`
   - The `groups` array lists group names/DNs the user belongs to

### Test 5: Manifest Integrity

1. Download any evidence package
2. Open `manifest.json`
3. **Expected:**
   - `files` array lists every file in the ZIP (except manifest.json itself)
   - Each file entry has `path`, `sha256`, and `size`
   - `hmacSha256Signature` is a 64-character hex string
4. Verify a checksum: compute SHA-256 of any file and compare against the manifest value

### Test 6: Rate Limiting

1. Click **Download Evidence Package** rapidly twice (or use curl/Postman)
2. **Expected:** Second request returns `429 Too Many Requests` with `Retry-After: 30`
3. Frontend shows: "An evidence package is already being generated. Please wait and try again."

### Test 7: Success Toast

1. Download a package successfully
2. **Expected:** Green toast appears showing "Evidence package downloaded successfully ({size})"
3. Toast auto-dismisses after 10 seconds

### Test 8: Disabled Campaigns Checkbox

1. Make sure no campaigns are selected
2. **Expected:** Download button is disabled (grayed out)

## Unit Tests

```bash
mvn test -Dtest=EvidencePackageServiceTest
```

12 test cases covering:
- Valid ZIP structure with standard files
- Campaign data inclusion (PDF, CSV, history JSON)
- SoD policies and violations JSON export
- User entitlements with group membership resolution
- Manifest checksums and HMAC signature
- SHA-256 correctness (empty string known hash)
- HMAC consistency and length
- ZIP with multiple entries
- Directory not found error
- Missing campaign graceful skip
- PDF generation failure graceful degradation
- HMAC signature varies with different content
