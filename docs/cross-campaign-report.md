# Cross-Campaign Reporting (Feature 3.5)

## Overview

Aggregates access review data across multiple campaigns in a directory, providing:
- Total decision counts (confirmed / revoked / pending) across campaigns
- Revocation rate and average campaign completion time
- Per-campaign metrics table
- Per-reviewer metrics (decisions, revocation rate, average response time)
- CSV and PDF export

## API Endpoints

All endpoints are scoped to a directory and require the `REPORTS_RUN` feature permission.

### GET `/api/v1/directories/{directoryId}/access-reviews/cross-campaign-report`

Returns aggregated cross-campaign metrics as JSON.

**Required parameters:**
| Param  | Type              | Description                          |
|--------|-------------------|--------------------------------------|
| `from` | ISO 8601 datetime | Start of date range (campaign creation date) |
| `to`   | ISO 8601 datetime | End of date range                    |

**Optional parameters:**
| Param    | Type   | Description                                      |
|----------|--------|--------------------------------------------------|
| `status` | string | Filter campaigns by status (UPCOMING, ACTIVE, CLOSED, CANCELLED, EXPIRED) |

**Example:**
```
GET /api/v1/directories/abc123/access-reviews/cross-campaign-report?from=2025-01-01T00:00:00Z&to=2026-03-24T23:59:59Z
```

### GET `/api/v1/directories/{directoryId}/access-reviews/cross-campaign-report/export`

Downloads the report as a file. Accepts the same `from`, `to`, and `status` parameters plus:

| Param    | Type   | Default | Description          |
|----------|--------|---------|----------------------|
| `format` | string | `csv`   | `csv` or `pdf`       |

## Frontend

Navigate to: **Access Reviews > Cross-Campaign Report** button (top-right of the campaign list page).

The view provides:
1. **Date range picker** — defaults to last 12 months
2. **Status filter** — optional filter by campaign status
3. **Generate Report** button — loads data
4. **Summary cards** — campaigns count, decisions, revocation rate, avg completion time
5. **Campaigns table** — click any row to navigate to that campaign's detail view
6. **Reviewers table** — per-reviewer decision stats
7. **Export CSV / Export PDF** buttons — download the report

## Manual Testing Checklist

### Prerequisites
- Running LDAPAdmin instance with PostgreSQL
- At least one directory configured with LDAP connection
- 2+ access review campaigns in various statuses (CLOSED, ACTIVE, etc.)
- Campaigns should have decisions submitted by multiple reviewers

### Test Cases

1. **Basic report generation**
   - Navigate to Access Reviews page for a directory
   - Click "Cross-Campaign Report"
   - Set date range to cover existing campaigns
   - Click "Generate Report"
   - Verify: summary cards show correct totals, campaigns table lists all campaigns, reviewers table shows reviewer stats

2. **Status filter**
   - Select "CLOSED" from the Status dropdown
   - Click "Generate Report"
   - Verify: only closed campaigns appear in the table

3. **Empty date range**
   - Set date range to a period with no campaigns
   - Click "Generate Report"
   - Verify: summary shows zeroes, tables show "No campaigns" / "No reviewer data" messages

4. **CSV export**
   - Generate a report with data
   - Click "Export CSV"
   - Verify: file downloads with `.csv` extension, contains campaign rows and reviewer rows separated by headers

5. **PDF export**
   - Generate a report with data
   - Click "Export PDF"
   - Verify: file downloads as valid PDF with Summary, Campaigns, and Reviewers sections

6. **Campaign drill-down**
   - Click a campaign row in the table
   - Verify: navigates to the campaign detail view

7. **Authorization**
   - As a user without `REPORTS_RUN` permission, attempt to access the report
   - Verify: 403 Forbidden

8. **Missing required params**
   - Call the API endpoint without `from` or `to` parameter
   - Verify: 400 Bad Request

## Files Changed

### New files
- `src/main/java/com/ldapadmin/dto/accessreview/CrossCampaignReportDto.java`
- `src/main/java/com/ldapadmin/dto/accessreview/CampaignMetricRow.java`
- `src/main/java/com/ldapadmin/dto/accessreview/ReviewerMetricRow.java`
- `src/main/java/com/ldapadmin/service/CrossCampaignReportService.java`
- `src/main/java/com/ldapadmin/controller/directory/CrossCampaignReportController.java`
- `src/test/java/com/ldapadmin/service/CrossCampaignReportServiceTest.java`
- `src/test/java/com/ldapadmin/controller/CrossCampaignReportControllerTest.java`
- `frontend/src/api/crossCampaignReport.js`
- `frontend/src/views/accessReviews/CrossCampaignReportView.vue`

### Modified files
- `src/main/java/com/ldapadmin/repository/AccessReviewCampaignRepository.java` — added `findByDirectoryIdAndCreatedAtBetween` query
- `src/main/java/com/ldapadmin/repository/AccessReviewDecisionRepository.java` — added `findDecidedByCampaignIds` query
- `frontend/src/router/index.js` — added `crossCampaignReport` route
- `frontend/src/views/accessReviews/CampaignListView.vue` — added "Cross-Campaign Report" button
