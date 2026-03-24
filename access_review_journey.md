# Install to Access Review Report: The Actual User Journey

## Step 1: Deploy the Application

**What happens:** User creates a `.env` file and runs Docker Compose.

```bash
# Create .env with required secrets
ENCRYPTION_KEY=<32-byte-key>
JWT_SECRET=<random-secret>
BOOTSTRAP_SUPERADMIN_PASSWORD=<initial-password>

# Launch
docker compose up -d
```

PostgreSQL starts first (health-checked), then the app on port 8080. Flyway runs 37 migrations automatically. A bootstrap superadmin account is created in memory (not persisted to DB until first permanent admin is created).

**Friction point:** No documentation tells the user what `ENCRYPTION_KEY` format to use, how long `JWT_SECRET` should be, or that the bootstrap account is temporary. They'd need to read the docker-compose.yml and guess.

---

## Step 2: First Login

**What happens:** User opens `http://localhost:8080`, sees the login screen, enters `superadmin` / the bootstrap password.

**API called:** `POST /api/v1/auth/login` -> `AuthController.java` -> `AuthenticationService.login()`

- Looks up account by username (must be active)
- For LOCAL auth: verifies bcrypt password_hash
- For LDAP auth: binds to configured LDAP auth server
- Issues JWT (signed, httpOnly cookie, 60-min expiry)
- Updates `last_login_at` timestamp

**Friction point:** The user has no way to know the default username is `superadmin` unless they read the docker-compose.yml `BOOTSTRAP_SUPERADMIN_USERNAME` default. There's no "first-run" indicator or setup wizard.

---

## Step 3: Connect to an LDAP Directory

**Where:** Superadmin settings > Directory Connections (`DirectoriesManageView.vue`)

**What the user fills in:**

| Field | Example | Required |
|---|---|---|
| Display name | "Corporate LDAP" | Yes |
| Host | ldap.example.com | Yes |
| Port | 389 | Yes (default 389) |
| SSL mode | NONE / LDAPS / STARTTLS | Yes |
| Bind DN | cn=admin,dc=example,dc=com | Yes |
| Bind password | (encrypted at rest with AES-256) | Yes |
| Base DN | dc=example,dc=com | Yes |
| User base DNs | ou=people,dc=example,dc=com | Yes |
| Group base DNs | ou=groups,dc=example,dc=com | Yes |

**API called:** `POST /api/v1/superadmin/directories` -> `DirectoryConnectionController.java`

- Validates connection with test LDAP bind
- Encrypts bind password with AES-256 (ENCRYPTION_KEY)
- Saves to `directory_connections` table
- Creates LDAP connection pool

**Test endpoint:** `POST /api/v1/superadmin/directories/test` (allows user to test connection before saving)

**Friction point:** The user must know their LDAP schema intimately — there's no "test connection" feedback clearly wired in the UI. They must manually enter base DNs rather than browsing the tree first.

---

## Step 4: Create a Provisioning Profile

**Where:** Superadmin > Profiles (`SuperadminProfilesView.vue` — 1,205 lines)

**What the user configures:**

- Profile name, associated directory
- Target OU for user creation (e.g., `ou=staff,dc=example,dc=com`)
- RDN attribute (e.g., `uid`)
- Object classes (e.g., `inetOrgPerson`, `posixAccount`)
- Attribute configuration (which LDAP attributes to show/require, input types, validation)
- Group assignments (default groups for new users)
- Optionally: approval workflow settings, password policy, self-registration

**API called:** `POST /api/v1/directories/{directoryId}/profiles` -> `ProvisioningProfileController.java`

**Why this matters for access reviews:** The profile defines which groups are managed and which users are in scope. Without it, the access review has no governance context.

**Friction point:** This is a complex multi-tab configuration view. For someone who just wants to run an access review, it's a significant detour. There's no "quick setup" path that infers reasonable defaults from the LDAP schema.

---

## Step 5: Create an Access Review Campaign

**Where:** Access Reviews > New Campaign (`CampaignCreateView.vue`)

**What the user fills in:**

### Campaign Details

| Field | Example | Required |
|---|---|---|
| Name | "Q1 2026 Access Review" | Yes |
| Description | "Quarterly recertification" | No |
| Deadline (days) | 30 | Yes |
| Recurrence (months) | 3 (quarterly) | No |
| Auto-revoke on decision | checkbox | No |
| Auto-revoke on expiry | checkbox | No |

### Groups to Review (at least one)

| Field | Example | Required |
|---|---|---|
| Group DN | cn=admins,ou=groups,dc=example,dc=com | Yes |
| Member attribute | member / uniqueMember / memberUid | Yes |
| Reviewer | (dropdown of admin accounts) | Yes |

**Reviewer lookup:** `GET /api/v1/directories/{directoryId}/access-reviews/reviewers`

**API called:** `POST /api/v1/directories/{directoryId}/access-reviews` -> `AccessReviewController.java` -> `AccessReviewCampaignService.create()`

**What the system does:**
- Validates deadlineDays >= 1
- Creates `AccessReviewCampaign` with status = `UPCOMING`
- Creates `AccessReviewGroup` for each group in request
- Resolves group name from LDAP (if available)
- Records audit event: `CAMPAIGN_CREATED`
- No LDAP member reads happen yet

**Friction point:** The user must type group DNs manually — no group picker or search. They must already have created admin accounts to assign as reviewers. If they only have the bootstrap superadmin, they're both the creator and the only reviewer option.

---

## Step 6: Activate the Campaign

**Where:** Campaign Detail view (`CampaignDetailView.vue`) > "Activate" button

**What happens on click:**

1. Confirmation dialog: _"Activating will snapshot current LDAP group members and notify assigned reviewers. Continue?"_
2. System reads current members of each group from LDAP
3. Creates a decision record for each member (status: PENDING)
4. Sets campaign status to `ACTIVE`
5. Records status change in campaign history
6. Sets deadline date (now + deadlineDays)

**API called:** `POST /api/v1/directories/{directoryId}/access-reviews/{campaignId}/activate`

**What the user sees after activation:**

- Progress dashboard: Total / Confirmed / Revoked / Pending counts
- Per-group progress bars
- Review groups table with "Review" button for each group
- Status history timeline

---

## Step 7: Review Group Memberships

**Where:** Review Decisions view (`ReviewDecisionsView.vue`) — reached by clicking "Review" on a group

**What the reviewer sees:**

A table of all group members with columns:
- Member display name
- Member DN
- Decision (PENDING / CONFIRM / REVOKE)
- Decided by
- Decided at

**Actions available:**

| Action | What it does |
|---|---|
| **Confirm** (per member) | Marks membership as approved — one click |
| **Revoke** (per member) | Opens modal for revocation comment, then removes member from LDAP group if auto-revoke is on |
| **Confirm All Remaining** (bulk) | One-click confirms all pending members |

**Filter tabs:** All / Pending / Confirmed / Revoked (with counts)

**API called:**
- Per-member: `POST /api/v1/directories/{dirId}/access-reviews/{campaignId}/groups/{groupId}/decisions/{decisionId}`
  - Request body: `{ decision: "CONFIRM"|"REVOKE", comment: string|null }`
- Bulk: `POST /api/v1/directories/{dirId}/access-reviews/{campaignId}/groups/{groupId}/decisions/bulk`
  - Request body: `{ items: [{ decisionId, decision, comment }, ...] }`

**What happens on REVOKE with auto-revoke enabled:**
- Calls `LdapGroupService.removeMember(dir, groupDn, memberAttribute, memberDn)`
- Sets `revoked_at = now()` on the decision
- Records audit: `REVIEW_AUTO_REVOKED`

**Friction point:** No "revoke all" bulk action. No filtering by last-login or risk. The reviewer is making decisions purely based on the member name/DN with no additional context (no "when was this person added?" or "what other groups are they in?").

---

## Step 8: Close the Campaign

**Where:** Campaign Detail view > "Close" or "Force Close" button

| Button | Behavior |
|---|---|
| **Close** | Only works if all decisions are made |
| **Force Close** | Closes even with pending decisions |
| **Cancel** | Abandons the campaign entirely |

**API called:** `POST /api/v1/directories/{directoryId}/access-reviews/{campaignId}/close?force=true|false`

**What happens on close:**
- Campaign status set to `CLOSED`
- If auto-revoke on expiry was enabled, pending members may be revoked
- `completedAt = now()` set on campaign
- If `recurrenceMonths` configured: creates next campaign (still requires manual activation)
- Status history updated
- Audit events recorded: `CAMPAIGN_CLOSED`

---

## Step 9: Export the Access Review Report

**Where:** Campaign Detail view > "Export CSV" button

**API called:** `GET /api/v1/directories/{dirId}/access-reviews/{campaignId}/export?format=csv`

**What the user gets:** A CSV file named `access-review-{campaignId}.csv` downloaded to their browser. Contains the decisions, member DNs, reviewers, timestamps.

### Alternative Reporting Paths

**On-demand reports** (`ReportJobsView.vue` > "Run Report Now"):
- Users in Group, Users in Branch, Users with No Group
- Recently Added / Modified / Deleted
- Disabled Accounts, Missing Profile Groups
- Output: CSV download
- Note: These are LDAP operational reports, NOT access review reports

**Audit log query** (`AuditLogView.vue`):
- Filter by action: `CAMPAIGN_CREATED`, `CAMPAIGN_ACTIVATED`, `CAMPAIGN_CLOSED`, `REVIEW_CONFIRMED`, `REVIEW_REVOKED`, `REVIEW_AUTO_REVOKED`
- Filter by directory, date range, actor
- Provides full activity trail of all review actions

**Scheduled report jobs** (`ReportJobsView.vue` > "Scheduled Jobs"):
- Cron-based scheduling with email or S3 delivery
- Same report types as on-demand (not access review reports)

---

## Total: 9 Steps, ~45-90 Minutes for a First-Time User

## Complete Navigation Map

| Step | Route | Vue Component | Key API |
|---|---|---|---|
| Install | N/A | N/A | Bootstrap via `BootstrapService` |
| Login | `/login` | `LoginView.vue` | `POST /api/v1/auth/login` |
| Dashboard | `/superadmin/dashboard` | `DashboardView.vue` | `GET /api/v1/dashboard` |
| Add Directory | `/superadmin/directories` | `DirectoriesManageView.vue` | `POST /api/v1/superadmin/directories` |
| Create Profile | `/superadmin/profiles` | `SuperadminProfilesView.vue` | `POST /api/v1/directories/{dirId}/profiles` |
| List Campaigns | `/directories/{dirId}/access-reviews` | `CampaignListView.vue` | `GET /api/v1/directories/{dirId}/access-reviews` |
| New Campaign | `/directories/{dirId}/access-reviews/new` | `CampaignCreateView.vue` | `POST /api/v1/directories/{dirId}/access-reviews` |
| Campaign Detail | `/directories/{dirId}/access-reviews/{id}` | `CampaignDetailView.vue` | `GET/POST .../activate`, `close`, `cancel`, `export` |
| Review Decisions | `/directories/{dirId}/access-reviews/{id}/groups/{gid}` | `ReviewDecisionsView.vue` | `GET/POST .../decisions`, `.../decisions/bulk` |
| Reports | `/directories/{dirId}/reports` | `ReportJobsView.vue` | `POST /api/v1/directories/{dirId}/reports/run` |
| Audit Log | `/superadmin/audit-log` | `AuditLogView.vue` | `GET /api/v1/audit` |

## Database Tables for Access Reviews

```
access_review_campaigns
├── id (UUID, PK)
├── directory_id (FK -> directory_connections)
├── name, description
├── status (UPCOMING, ACTIVE, CLOSED, CANCELLED, EXPIRED)
├── deadline, deadline_days
├── recurrence_months
├── auto_revoke, auto_revoke_on_expiry
├── created_by (FK -> accounts)
└── created_at, updated_at, completed_at

access_review_groups
├── id (UUID, PK)
├── campaign_id (FK -> access_review_campaigns)
├── group_dn, group_name
├── member_attribute
└── reviewer_id (FK -> accounts)

access_review_decisions
├── id (UUID, PK)
├── review_group_id (FK -> access_review_groups)
├── member_dn, member_display
├── decision (CONFIRM, REVOKE, null=PENDING)
├── comment
├── decided_by (FK -> accounts)
├── decided_at, revoked_at
└── UNIQUE(review_group_id, member_dn)

access_review_campaign_history
├── id (UUID, PK)
├── campaign_id (FK -> access_review_campaigns)
├── old_status, new_status
├── changed_by (FK -> accounts)
├── changed_at
└── note
```

All tables created in `V22__access_review_campaigns.sql`, recurrence added in `V23__access_review_recurrence.sql`, status rename in `V24__rename_draft_to_upcoming.sql`.

---

## The Gaps That Hurt

| Gap | Impact |
|---|---|
| No setup wizard | Steps 1-4 are trial-and-error |
| No group picker in campaign creation | User must know DNs by heart or copy from the DIT browser |
| No reviewer context | Reviewer sees names but not risk, tenure, or activity data |
| CSV-only export | Auditors typically want formatted PDF with summary |
| No access review in scheduled reports | Can't automate the most important compliance artifact |
| No cross-campaign reporting | "Show me all reviews for the past year" doesn't exist |
| No dashboard overview | No single screen showing compliance posture |
| Recurrence not fully automatic | Next campaign is created but must be manually activated |
| No campaign templates | Every campaign created from scratch |
| No bulk revoke | Bulk confirm exists but bulk revoke requires per-member modal |

The core workflow works. But the distance between "working" and "pleasant" is significant — and for a compliance tool, that distance is the difference between a customer who renews and one who goes back to spreadsheets.
