# Feature 2.2: Compliance Posture Dashboard

## Overview

Redesigns the superadmin dashboard from a basic stats view into a compliance posture dashboard with color-coded health indicators, drill-down navigation, approval aging analysis, and per-campaign progress tracking.

## What Was Built

### Backend

| Component | File |
|---|---|
| ComplianceDashboardDto | `src/main/java/com/ldapadmin/dto/dashboard/ComplianceDashboardDto.java` |
| DashboardService (rewritten) | `src/main/java/com/ldapadmin/service/DashboardService.java` |
| DashboardController (updated) | `src/main/java/com/ldapadmin/controller/superadmin/DashboardController.java` |
| PendingApprovalRepository (new queries) | `findAllByStatus()`, `countByStatus()` |
| AccessReviewCampaignRepository (new queries) | `findByStatus()`, `countByStatusAndDeadlineBefore()` |
| AccessReviewDecisionRepository (new query) | `countDistinctReviewedUsersSince()` |

### Frontend

| Component | File |
|---|---|
| DashboardView.vue (redesigned) | `frontend/src/views/superadmin/DashboardView.vue` |

## New Compliance Metrics

| Metric | Source | Description |
|---|---|---|
| Open SoD Violations | `SodViolationRepository.countByStatus(OPEN)` | Total open separation-of-duties violations |
| Campaign Completion % | Decision counts across all active campaigns | `(decided / total) * 100` across all active campaigns |
| Overdue Campaigns | `countByStatusAndDeadlineBefore(ACTIVE, now)` | Active campaigns past their deadline |
| Users Not Reviewed (90d) | Total LDAP users minus distinct reviewed users | Users whose DN has no review decision in the last 90 days |
| Approval Aging | Bucket pending approvals by `createdAt` age | <24h, 1-3d, 3-7d, 7+ days |
| Per-Campaign Progress | Decision counts per campaign | Individual completion % with overdue flag |
| Per-Directory SoD Violations | SoD violations per directory | Open violations shown in directory breakdown table |

## Dashboard Layout

```
┌─────────────────┬──────────────────┬───────────────────┬──────────────────┐
│ SoD Violations  │ Campaign         │ Pending           │ Overdue          │
│ (click→violations)│ Completion %   │ Approvals         │ Campaigns        │
│ RED/GREEN/YELLOW│ RED/GREEN/YELLOW │ (click→approvals) │ RED/GREEN        │
├─────────────────┴──────────────────┴───────────────────┴──────────────────┤
│ Total Users        │ Total Groups        │ Users Not Reviewed (90d)       │
├────────────────────┴─────────────────────┴────────────────────────────────┤
│ Approval Aging (bar chart)   │ Active Campaign Progress (progress bars)  │
├──────────────────────────────┴───────────────────────────────────────────┤
│ Directories Table            │ Recent Activity                           │
│ (+ SoD violations column)    │ (includes SoD audit labels)               │
└──────────────────────────────┴───────────────────────────────────────────┘
```

## Color Coding Thresholds

| Metric | Green | Yellow | Red |
|---|---|---|---|
| SoD Violations | 0 | 1-5 | 6+ |
| Campaign Completion | >= 90% | 50-89% | < 50% |
| Overdue Campaigns | 0 | — | 1+ |
| Users Not Reviewed | 0 | 1-10 | 11+ |
| Pending Approvals | 0 | 1-10 | 11+ |

## API Response Shape

`GET /api/v1/superadmin/dashboard` now returns `ComplianceDashboardDto`:

```json
{
  "totalUsers": 150,
  "totalGroups": 25,
  "totalPendingApprovals": 8,
  "openSodViolations": 3,
  "campaignCompletionPercent": 72.5,
  "overdueCampaigns": 1,
  "usersNotReviewedIn90Days": 42,
  "approvalAging": {
    "lessThan24h": 3,
    "oneToThreeDays": 2,
    "threeToSevenDays": 1,
    "moreThanSevenDays": 2
  },
  "campaignProgress": [
    {
      "campaignId": "...",
      "campaignName": "Q1 Access Review",
      "directoryName": "Corporate AD",
      "totalDecisions": 100,
      "decidedCount": 72,
      "completionPercent": 72.0,
      "overdue": false,
      "deadline": "2026-04-15T00:00:00Z"
    }
  ],
  "directories": [
    {
      "id": "...",
      "name": "Corporate AD",
      "enabled": true,
      "userCount": 150,
      "groupCount": 25,
      "pendingApprovals": 8,
      "activeCampaigns": 2,
      "openSodViolations": 3
    }
  ],
  "recentAudit": [...]
}
```

## Manual Testing Guide

### Prerequisites

1. Running LDAPAdmin with at least one connected LDAP directory
2. Superadmin account
3. For full testing: some SoD policies with violations (Feature 2.1), active access review campaigns, and pending approvals

### Test 1: Dashboard Loads with New Layout

1. Log in as superadmin
2. Navigate to `/superadmin/dashboard`
3. **Expected**: Four compliance posture cards at top (SoD Violations, Campaign Completion, Pending Approvals, Overdue Campaigns)
4. Second row shows Total Users, Total Groups, Users Not Reviewed
5. Middle row shows Approval Aging bar chart and Campaign Progress bars
6. Bottom row shows Directories table (now with SoD column) and Recent Activity

### Test 2: Color Coding

1. Create some SoD violations (via Feature 2.1 scan)
2. Refresh dashboard
3. **Expected**: SoD Violations card shows red border/text for 6+ violations, yellow for 1-5, green for 0
4. Similarly verify other cards have appropriate color coding

### Test 3: Drill-Down Navigation

1. Click the "Open SoD Violations" card
2. **Expected**: Navigates to the first enabled directory's SoD violations page
3. Go back, click "Pending Approvals" card
4. **Expected**: Navigates to the approvals page
5. Click "Campaign Completion" or "Overdue Campaigns"
6. **Expected**: Navigates to access reviews page

### Test 4: Approval Aging Visualization

1. Create several pending approvals at different times (or wait for them to age)
2. Refresh dashboard
3. **Expected**: Horizontal bar chart shows approvals bucketed by age
4. Bars are color-coded: green (<24h), yellow (1-3d), amber (3-7d), red (7+d)

### Test 5: Campaign Progress Bars

1. Create and activate an access review campaign
2. Make some review decisions (confirm/revoke)
3. Refresh dashboard
4. **Expected**: Campaign appears in "Active Campaign Progress" with:
   - Progress bar showing completion percentage
   - Decided/total count (e.g., "30/50")
   - OVERDUE badge if past deadline
   - Color changes based on completion %

### Test 6: Per-Directory SoD Column

1. Ensure SoD violations exist for a directory
2. Refresh dashboard
3. **Expected**: Directories table shows "SoD" column with violation count per directory
4. Count shown in red when > 0, green "0" when no violations

### Test 7: Users Not Reviewed (90 days)

1. Check the "Users Not Reviewed (90d)" card
2. **Expected**: Shows total users minus those with review decisions in the last 90 days
3. If no campaigns have been run, this equals total user count

## Unit Tests

```bash
mvn test -Dtest=DashboardServiceTest
```

8 test cases covering:
- Basic DTO structure and field mapping
- Campaign completion % calculation (80/100 = 80%)
- Overdue campaign detection
- Approval aging bucket computation (4 approvals in 4 buckets)
- Users not reviewed calculation (50 users - 30 reviewed = 20)
- No campaigns → 100% completion
- Disabled directory → LDAP calls skipped
- Per-directory SoD violation counts
