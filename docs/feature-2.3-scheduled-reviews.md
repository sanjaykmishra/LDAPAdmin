# Feature 2.3: Scheduled Access Reviews with Auto-Reminders

## Overview

This feature adds automated escalation workflows, duplicate-safe reminder tracking, and auto-revoke on campaign expiry to the access review system.

## What Was Built

### Backend

| Component | File |
|---|---|
| DB migration | `src/main/resources/db/migration/V42__campaign_reminder_tracking.sql` |
| Entity | `src/main/java/com/ldapadmin/entity/CampaignReminder.java` |
| Enum | `src/main/java/com/ldapadmin/entity/enums/ReminderType.java` |
| Repository | `src/main/java/com/ldapadmin/repository/CampaignReminderRepository.java` |
| DTO | `src/main/java/com/ldapadmin/dto/accessreview/CampaignReminderDto.java` |
| Scheduler (modified) | `src/main/java/com/ldapadmin/service/AccessReviewScheduler.java` |
| Notification (modified) | `src/main/java/com/ldapadmin/service/AccessReviewNotificationService.java` |
| Service (modified) | `src/main/java/com/ldapadmin/service/AccessReviewCampaignService.java` |
| Controller (modified) | `src/main/java/com/ldapadmin/controller/directory/AccessReviewController.java` |
| Config (modified) | `src/main/resources/application.yml` |

### Frontend

| Component | File |
|---|---|
| Campaign create form (modified) | `frontend/src/views/accessReviews/CampaignCreateView.vue` |
| Campaign detail view (modified) | `frontend/src/views/accessReviews/CampaignDetailView.vue` |
| API layer (modified) | `frontend/src/api/accessReviews.js` |

## Configuration

Three new properties in `application.yml` (also settable via environment variables):

| Property | Env Var | Default | Description |
|---|---|---|---|
| `ldapadmin.access-review.escalation-days` | `ACCESS_REVIEW_ESCALATION_DAYS` | `14` | Days after campaign activation before escalation emails are sent for non-responsive reviewers |
| `ldapadmin.access-review.auto-revoke-enabled` | `ACCESS_REVIEW_AUTO_REVOKE_ENABLED` | `false` | Global kill switch for auto-revoke on expiry. Must be `true` for auto-revoke to execute |
| `ldapadmin.access-review.reminder-days` | `ACCESS_REVIEW_REMINDER_DAYS` | `3` | Days before deadline to send reminder (existing, now tracked in DB) |

## New API Endpoint

```
GET /api/v1/directories/{dirId}/access-reviews/{campaignId}/reminders
```

Returns a list of reminder/escalation records for the campaign:

```json
[
  {
    "id": "uuid",
    "reminderType": "DEADLINE|ESCALATION",
    "reviewerUsername": "jdoe",
    "reviewerAccountId": "uuid",
    "sentAt": "2026-03-24T02:00:00Z"
  }
]
```

## Manual Testing Guide

### Prerequisites

1. Running LDAPAdmin instance with SMTP configured (or check server logs for email content)
2. At least one LDAP directory connected with groups containing members
3. Two admin accounts: one as campaign creator, one as reviewer

### Test 1: Deadline Reminders with Duplicate Prevention

1. Create a campaign with `deadlineDays: 1` (short deadline for testing)
2. Activate the campaign
3. Wait for the scheduler to run (daily at 2 AM), or trigger manually:
   - Temporarily set `ACCESS_REVIEW_EXPIRY_CRON=0 */1 * * * ?` for every-minute runs
4. **Expected**: Reviewer receives one deadline reminder email
5. Wait for the next scheduler cycle
6. **Expected**: No duplicate reminder — check the DB: `SELECT * FROM campaign_reminders WHERE campaign_id = '...'` should show exactly one DEADLINE row per reviewer
7. Check the campaign detail page — the "Reminder & Escalation History" section should show the DEADLINE entry

### Test 2: Escalation After 14 Days

1. Create and activate a campaign with `deadlineDays: 30`
2. To simulate time passing, update the campaign's `created_at` in the DB:
   ```sql
   UPDATE access_review_campaigns SET created_at = now() - interval '15 days' WHERE id = '...';
   ```
3. Do NOT submit any decisions as the reviewer
4. Wait for the scheduler to run
5. **Expected**: Campaign creator receives an escalation email mentioning the non-responsive reviewer
6. Check the campaign detail page — the reviewer should show an "ESCALATED" badge
7. Check DB: `SELECT * FROM campaign_reminders WHERE reminder_type = 'ESCALATION'` should have one row
8. Wait for the next cycle — **Expected**: No duplicate escalation

### Test 3: Auto-Revoke on Expiry

1. Set environment variable: `ACCESS_REVIEW_AUTO_REVOKE_ENABLED=true`
2. Create a campaign with `deadlineDays: 1` and check "Auto-revoke on expiry"
3. Activate the campaign
4. Leave some decisions undecided
5. Wait for the deadline to pass and the scheduler to run
6. **Expected**:
   - All undecided memberships get REVOKE decisions with comment "Auto-revoked on campaign expiry"
   - Members are removed from the LDAP groups
   - Audit events logged with action `review.auto_revoked` and reason `auto-revoke-on-expiry`
7. Verify in the LDAP directory that the members were actually removed

### Test 4: Auto-Revoke Kill Switch

1. Set `ACCESS_REVIEW_AUTO_REVOKE_ENABLED=false` (default)
2. Create a campaign with "Auto-revoke on expiry" enabled
3. Let it expire
4. **Expected**: Campaign expires normally but no memberships are revoked, even though `autoRevokeOnExpiry` is set on the campaign

### Test 5: Frontend Campaign Creation Form

1. Navigate to campaign creation
2. Check "Auto-revoke on expiry" — **Expected**: Orange warning text appears
3. Set recurrence months and deadline days — **Expected**: "Next scheduled run" date is displayed
4. Escalation info text should be visible explaining the reminder/escalation workflow

### Test 6: Campaign Detail View

1. Open a campaign that has had reminders/escalations sent
2. **Expected**: "Per-Reviewer Progress" section shows progress bars per reviewer
3. **Expected**: Reviewers with escalations show red "ESCALATED" badge
4. **Expected**: "Reminder & Escalation History" section lists all sent reminders with timestamps

## Unit Tests

Run the feature-specific tests:

```bash
mvn test -Dtest=AccessReviewSchedulerTest
mvn test -Dtest=AccessReviewNotificationServiceTest
mvn test -Dtest=AccessReviewCampaignServiceTest
```

Test coverage includes:
- Deadline reminder sending and duplicate prevention
- Escalation threshold detection and notification
- Escalation deduplication
- Skipping reviewers with no pending decisions
- Auto-revoke of undecided memberships on expiry
- Partial failure handling during auto-revoke (continues processing remaining)
- Notification service graceful handling when SMTP is not configured
