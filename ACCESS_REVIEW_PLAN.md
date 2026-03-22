# Access Review / Recertification Campaigns — Implementation Plan

## Overview

Access review campaigns allow an organization to periodically verify that group memberships are still appropriate. A campaign owner selects target groups, assigns reviewers, and sets a deadline. Each reviewer sees the members of their assigned groups and must **confirm** or **revoke** each membership. The campaign produces a full audit trail suitable for SOX, SOC 2, and ISO 27001 compliance evidence.

This feature builds on existing infrastructure: LDAP group member queries (`LdapGroupService`), the approval/notification email system (`ApprovalNotificationService`), the audit framework (`AuditService`), and the `@RequiresFeature` permission model.

---

## Architecture Decisions

### Campaign scoping — Directory-level, not Realm-level
Campaigns target groups by DN. A single campaign may span multiple group base DNs within the same directory. The campaign is scoped to a `directory_id`, consistent with how report jobs and approvals work. Realm filtering is optional (for UI convenience), not a hard boundary.

### Reviewer model — Admin accounts, not LDAP users
Reviewers are admins from the `accounts` table, consistent with the existing approval workflow. They authenticate through the normal login flow and see their assigned reviews in the UI. This avoids building a separate self-service portal for now.

### Decision granularity — Per-member, per-group
Each reviewer decides on individual member–group pairs, not entire groups wholesale. This gives the finest audit granularity and matches how real access reviews are conducted.

### Revocation execution — Manual vs. automatic
When a reviewer marks a membership for revocation, the system can either:
1. **Auto-revoke** — immediately remove the member from the LDAP group
2. **Flag only** — mark for revocation; a separate admin action removes them

We implement both, controlled by a campaign-level setting `auto_revoke`. Default is `false` (flag only) to prevent accidental mass removal. When auto-revoke is on, the LDAP removal uses `LdapGroupService.removeMember()` and records an audit event.

### What stays the same
- No changes to existing entities, controllers, or LDAP services
- Existing `@RequiresFeature` aspect and `PermissionService` unchanged
- Audit event recording via existing `AuditService.record()` pattern

---

## 1. Database Migration — `V20__access_review_campaigns.sql`

**File:** `src/main/resources/db/migration/V20__access_review_campaigns.sql`

```sql
-- Access review / recertification campaigns

CREATE TABLE access_review_campaigns (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    directory_id    UUID         NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    starts_at       TIMESTAMPTZ,
    deadline        TIMESTAMPTZ  NOT NULL,
    auto_revoke     BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_revoke_on_expiry BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT pk_access_review_campaigns PRIMARY KEY (id),
    CONSTRAINT fk_arc_directory FOREIGN KEY (directory_id) REFERENCES directory_connections (id) ON DELETE CASCADE,
    CONSTRAINT fk_arc_creator   FOREIGN KEY (created_by)   REFERENCES accounts (id),
    CONSTRAINT chk_arc_status   CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_arc_directory_status ON access_review_campaigns (directory_id, status);

CREATE TABLE access_review_groups (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    campaign_id     UUID         NOT NULL,
    group_dn        VARCHAR(1000) NOT NULL,
    group_name      VARCHAR(500),
    member_attribute VARCHAR(50) NOT NULL DEFAULT 'member',
    reviewer_id     UUID         NOT NULL,
    CONSTRAINT pk_access_review_groups  PRIMARY KEY (id),
    CONSTRAINT uq_arg_campaign_group    UNIQUE (campaign_id, group_dn),
    CONSTRAINT fk_arg_campaign FOREIGN KEY (campaign_id) REFERENCES access_review_campaigns (id) ON DELETE CASCADE,
    CONSTRAINT fk_arg_reviewer FOREIGN KEY (reviewer_id) REFERENCES accounts (id)
);

CREATE TABLE access_review_decisions (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    review_group_id UUID         NOT NULL,
    member_dn       VARCHAR(1000) NOT NULL,
    member_display  VARCHAR(500),
    decision        VARCHAR(20),
    comment         TEXT,
    decided_by      UUID,
    decided_at      TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    CONSTRAINT pk_access_review_decisions PRIMARY KEY (id),
    CONSTRAINT uq_ard_group_member       UNIQUE (review_group_id, member_dn),
    CONSTRAINT fk_ard_review_group FOREIGN KEY (review_group_id) REFERENCES access_review_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_ard_decided_by   FOREIGN KEY (decided_by)      REFERENCES accounts (id),
    CONSTRAINT chk_ard_decision    CHECK (decision IS NULL OR decision IN ('CONFIRM', 'REVOKE'))
);

CREATE INDEX idx_ard_review_group ON access_review_decisions (review_group_id);
CREATE INDEX idx_ard_undecided    ON access_review_decisions (review_group_id) WHERE decision IS NULL;

-- Campaign status transition history (audit trail for campaign lifecycle)
CREATE TABLE access_review_campaign_history (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    campaign_id     UUID         NOT NULL,
    old_status      VARCHAR(20),
    new_status      VARCHAR(20)  NOT NULL,
    changed_by      UUID         NOT NULL,
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    note            TEXT,
    CONSTRAINT pk_arch PRIMARY KEY (id),
    CONSTRAINT fk_arch_campaign   FOREIGN KEY (campaign_id) REFERENCES access_review_campaigns (id) ON DELETE CASCADE,
    CONSTRAINT fk_arch_changed_by FOREIGN KEY (changed_by)  REFERENCES accounts (id)
);

CREATE INDEX idx_arch_campaign ON access_review_campaign_history (campaign_id, changed_at);

-- Add feature key to constraint
ALTER TABLE admin_feature_permissions
    DROP CONSTRAINT IF EXISTS chk_afp_feature_key;

ALTER TABLE admin_feature_permissions
    ADD CONSTRAINT chk_afp_feature_key CHECK (
        feature_key IN (
            'user.create', 'user.edit', 'user.delete',
            'user.enable_disable', 'user.move', 'user.reset_password',
            'group.edit', 'group.manage_members', 'group.create_delete',
            'bulk.import', 'bulk.export', 'bulk.attribute_update',
            'reports.run', 'reports.export', 'reports.schedule',
            'access_review.manage', 'access_review.review'
        )
    );
```

### Table design rationale

| Table | Purpose |
|-------|---------|
| `access_review_campaigns` | Top-level campaign definition with deadline, status, and ownership. `starts_at` allows scheduling future campaigns. `auto_revoke_on_expiry` controls whether uncertified memberships are auto-removed when the deadline passes. |
| `access_review_groups` | Links a group DN to a campaign and assigns a reviewer. Denormalizes `group_name` for display after LDAP entries change. |
| `access_review_decisions` | One row per member per group. Pre-populated when the campaign is activated by snapshotting current members from LDAP. `decision` is NULL until the reviewer acts. `decided_by` tracks the account that made each decision. |
| `access_review_campaign_history` | Tracks every status transition (DRAFT→ACTIVE, ACTIVE→CLOSED, etc.) with who triggered it and when. Provides a dedicated audit trail separate from the general audit log. |

---

## 2. Backend: New Enums

### `CampaignStatus` enum

**File:** `src/main/java/com/ldapadmin/entity/enums/CampaignStatus.java`

```java
public enum CampaignStatus {
    DRAFT, ACTIVE, CLOSED, CANCELLED, EXPIRED
}
```

- `EXPIRED` — campaign deadline passed without being explicitly closed; system transitions automatically via the scheduler

### `ReviewDecision` enum

**File:** `src/main/java/com/ldapadmin/entity/enums/ReviewDecision.java`

```java
public enum ReviewDecision {
    CONFIRM, REVOKE
}
```

### Add to `FeatureKey`

```java
ACCESS_REVIEW_MANAGE  ("access_review.manage"),
ACCESS_REVIEW_REVIEW  ("access_review.review");
```

### Add to `AuditAction`

```java
// ── Access review campaigns ─────────────────────────────────────────────
CAMPAIGN_CREATED     ("campaign.created"),
CAMPAIGN_ACTIVATED   ("campaign.activated"),
CAMPAIGN_CLOSED      ("campaign.closed"),
CAMPAIGN_CANCELLED   ("campaign.cancelled"),
CAMPAIGN_EXPIRED     ("campaign.expired"),
REVIEW_CONFIRMED     ("review.confirmed"),
REVIEW_REVOKED       ("review.revoked"),
REVIEW_AUTO_REVOKED  ("review.auto_revoked");
```

---

## 3. Backend: New Entities

### `AccessReviewCampaign`

**File:** `src/main/java/com/ldapadmin/entity/AccessReviewCampaign.java`

```java
@Entity
@Table(name = "access_review_campaigns")
@Getter @Setter @NoArgsConstructor
public class AccessReviewCampaign {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id")
    private DirectoryConnection directory;

    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private CampaignStatus status = CampaignStatus.DRAFT;

    private OffsetDateTime startsAt;
    private OffsetDateTime deadline;
    private boolean autoRevoke;
    private boolean autoRevokeOnExpiry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Account createdBy;

    @CreationTimestamp
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccessReviewGroup> reviewGroups = new ArrayList<>();
}
```

### `AccessReviewGroup`

**File:** `src/main/java/com/ldapadmin/entity/AccessReviewGroup.java`

```java
@Entity
@Table(name = "access_review_groups")
@Getter @Setter @NoArgsConstructor
public class AccessReviewGroup {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id")
    private AccessReviewCampaign campaign;

    private String groupDn;
    private String groupName;
    private String memberAttribute;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id")
    private Account reviewer;

    @OneToMany(mappedBy = "reviewGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccessReviewDecision> decisions = new ArrayList<>();
}
```

### `AccessReviewDecision`

**File:** `src/main/java/com/ldapadmin/entity/AccessReviewDecision.java`

```java
@Entity
@Table(name = "access_review_decisions")
@Getter @Setter @NoArgsConstructor
public class AccessReviewDecision {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_group_id")
    private AccessReviewGroup reviewGroup;

    private String memberDn;
    private String memberDisplay;

    @Enumerated(EnumType.STRING)
    private ReviewDecision decision;

    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private Account decidedBy;

    private OffsetDateTime decidedAt;
    private OffsetDateTime revokedAt;
}
```

### `AccessReviewCampaignHistory`

**File:** `src/main/java/com/ldapadmin/entity/AccessReviewCampaignHistory.java`

```java
@Entity
@Table(name = "access_review_campaign_history")
@Getter @Setter @NoArgsConstructor
public class AccessReviewCampaignHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id")
    private AccessReviewCampaign campaign;

    @Enumerated(EnumType.STRING)
    private CampaignStatus oldStatus;

    @Enumerated(EnumType.STRING)
    private CampaignStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by")
    private Account changedBy;

    @CreationTimestamp
    private OffsetDateTime changedAt;

    private String note;
}
```

---

## 4. Backend: Repositories

**File:** `src/main/java/com/ldapadmin/repository/`

```java
public interface AccessReviewCampaignRepository extends JpaRepository<AccessReviewCampaign, UUID> {
    Page<AccessReviewCampaign> findByDirectoryId(UUID directoryId, Pageable pageable);
    List<AccessReviewCampaign> findByDirectoryIdAndStatus(UUID directoryId, CampaignStatus status);
    List<AccessReviewCampaign> findByStatusAndDeadlineBefore(CampaignStatus status, OffsetDateTime deadline);
}

public interface AccessReviewGroupRepository extends JpaRepository<AccessReviewGroup, UUID> {
    List<AccessReviewGroup> findByCampaignId(UUID campaignId);
    List<AccessReviewGroup> findByCampaignIdAndReviewerId(UUID campaignId, UUID reviewerId);
}

public interface AccessReviewDecisionRepository extends JpaRepository<AccessReviewDecision, UUID> {
    List<AccessReviewDecision> findByReviewGroupId(UUID reviewGroupId);
    long countByReviewGroupIdAndDecisionIsNull(UUID reviewGroupId);
    long countByCampaignIdAndDecisionIsNull(UUID campaignId);
}
```

The last method on `AccessReviewDecisionRepository` requires a custom `@Query`:

```java
@Query("SELECT COUNT(d) FROM AccessReviewDecision d WHERE d.reviewGroup.campaign.id = :campaignId AND d.decision IS NULL")
long countPendingByCampaignId(@Param("campaignId") UUID campaignId);
```

```java
public interface AccessReviewCampaignHistoryRepository extends JpaRepository<AccessReviewCampaignHistory, UUID> {
    List<AccessReviewCampaignHistory> findByCampaignIdOrderByChangedAtAsc(UUID campaignId);
}
```

---

## 5. Backend: Service Layer

### `AccessReviewCampaignService`

**File:** `src/main/java/com/ldapadmin/service/AccessReviewCampaignService.java`

Responsibilities:
1. **Create campaign** (DRAFT) — validate name, deadline > now, groups exist in LDAP. Optional `startsAt` for scheduling future activation.
2. **Activate campaign** — transition DRAFT → ACTIVE:
   - For each `AccessReviewGroup`, query LDAP via `LdapGroupService.getMembers()` to snapshot current members
   - Create one `AccessReviewDecision` per member (decision = NULL)
   - Resolve member display names via `LdapUserService` (best-effort, fallback to DN)
   - Send notification emails to all assigned reviewers via `AccessReviewNotificationService`
   - Record status transition in `access_review_campaign_history`
   - Record `CAMPAIGN_ACTIVATED` audit event
3. **Close campaign** — transition ACTIVE → CLOSED:
   - Verify all decisions are made (or allow force-close with undecided items)
   - If `autoRevoke` is true, execute pending revocations via `LdapGroupService.removeMember()`
   - Set `completedAt`, record status transition in history, record `CAMPAIGN_CLOSED` audit event
4. **Cancel campaign** — transition DRAFT/ACTIVE → CANCELLED, record status transition
5. **Get campaign with progress** — return decision counts (total, confirmed, revoked, pending)
6. **List campaigns** — paginated, filtered by directory
7. **Get campaign history** — return status transition log for a campaign

```java
@Service
@RequiredArgsConstructor
public class AccessReviewCampaignService {

    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewGroupRepository groupRepo;
    private final AccessReviewDecisionRepository decisionRepo;
    private final AccessReviewCampaignHistoryRepository historyRepo;
    private final LdapGroupService ldapGroupService;
    private final LdapUserService ldapUserService;
    private final DirectoryConnectionRepository directoryRepo;
    private final AuditService auditService;
    private final AccessReviewNotificationService notificationService;
    private final PermissionService permissionService;

    public AccessReviewCampaign create(UUID directoryId, CreateCampaignRequest req, AuthPrincipal principal) { ... }
    public AccessReviewCampaign activate(UUID campaignId, AuthPrincipal principal) { ... }
    public AccessReviewCampaign close(UUID campaignId, boolean forceClose, AuthPrincipal principal) { ... }
    public AccessReviewCampaign cancel(UUID campaignId, AuthPrincipal principal) { ... }
    public Page<CampaignSummaryDto> list(UUID directoryId, AuthPrincipal principal, Pageable pageable) { ... }
    public CampaignDetailDto get(UUID campaignId, AuthPrincipal principal) { ... }
    public List<AccessReviewCampaignHistory> getHistory(UUID campaignId, AuthPrincipal principal) { ... }
}
```

### `AccessReviewDecisionService`

**File:** `src/main/java/com/ldapadmin/service/AccessReviewDecisionService.java`

Responsibilities:
1. **List decisions for a review group** — for the assigned reviewer
2. **Submit decision** (CONFIRM or REVOKE) on a single member
3. **Bulk decision** — confirm or revoke multiple members in one call
4. If `autoRevoke` is enabled and decision is REVOKE, immediately call `LdapGroupService.removeMember()` and set `revokedAt`
5. Record `REVIEW_CONFIRMED` / `REVIEW_REVOKED` / `REVIEW_AUTO_REVOKED` audit events

```java
@Service
@RequiredArgsConstructor
public class AccessReviewDecisionService {

    private final AccessReviewDecisionRepository decisionRepo;
    private final AccessReviewGroupRepository groupRepo;
    private final LdapGroupService ldapGroupService;
    private final DirectoryConnectionRepository directoryRepo;
    private final AuditService auditService;

    public List<DecisionDto> listForReviewGroup(UUID reviewGroupId, AuthPrincipal principal) { ... }
    public DecisionDto decide(UUID decisionId, ReviewDecision decision, String comment, AuthPrincipal principal) { ... }
    public List<DecisionDto> bulkDecide(UUID reviewGroupId, List<BulkDecisionItem> items, AuthPrincipal principal) { ... }
}
```

### `AccessReviewNotificationService`

**File:** `src/main/java/com/ldapadmin/service/AccessReviewNotificationService.java`

Follows the same pattern as `ApprovalNotificationService`:
- `notifyReviewersAssigned(campaign, reviewerAccounts)` — "You have been assigned as a reviewer"
- `notifyDeadlineApproaching(campaign)` — sent N days before deadline (configurable, default 3 days) with progress stats
- `notifyCampaignClosed(campaign)` — summary to campaign creator (X confirmed, Y revoked)
- `notifyCampaignExpired(campaign)` — sent to creator + reviewer when deadline passes without close

### `AccessReviewScheduler`

**File:** `src/main/java/com/ldapadmin/service/AccessReviewScheduler.java`

A `@Scheduled` bean (following the pattern of `sendPendingReminders()`) that runs daily:

1. **Expire overdue campaigns** — finds all ACTIVE campaigns past their deadline:
   - If `autoRevokeOnExpiry = true`: executes LDAP revocations for items with decision = REVOKE, records `REVIEW_AUTO_REVOKED` audit events
   - Transitions campaign status to EXPIRED, records history entry
   - Sends `notifyCampaignExpired()` to creator + reviewer
   - Records `CAMPAIGN_EXPIRED` audit event
2. **Send deadline reminders** — finds ACTIVE campaigns approaching deadline (configurable via `ldapadmin.access-review.reminder-days`, default 3):
   - Sends `notifyDeadlineApproaching()` with progress stats (X of Y items decided)
   - Only sends once (track via a flag or check if already sent for this deadline window)

```java
@Component
@RequiredArgsConstructor
public class AccessReviewScheduler {

    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewCampaignService campaignService;
    private final AccessReviewNotificationService notificationService;

    @Scheduled(cron = "${ldapadmin.access-review.expiry-cron:0 0 2 * * ?}")  // daily at 2am
    public void processDeadlines() { ... }
}
```

---

## 6. Backend: DTOs

**Package:** `com.ldapadmin.dto.accessreview`

| DTO | Purpose |
|-----|---------|
| `CreateCampaignRequest` | name, description, startsAt, deadline, autoRevoke, autoRevokeOnExpiry, groups (list of `{groupDn, memberAttribute, reviewerAccountId}`) |
| `CampaignSummaryDto` | id, name, status, startsAt, deadline, createdAt, progress (total/confirmed/revoked/pending counts) |
| `CampaignDetailDto` | Full campaign + list of `ReviewGroupDto` + list of `CampaignHistoryDto` |
| `ReviewGroupDto` | id, groupDn, groupName, reviewerUsername, decisionCounts |
| `DecisionDto` | id, memberDn, memberDisplay, decision, comment, decidedByUsername, decidedAt, revokedAt |
| `SubmitDecisionRequest` | decision (CONFIRM/REVOKE), comment |
| `BulkDecisionRequest` | List of `{decisionId, decision, comment}` |
| `CampaignProgressDto` | total, confirmed, revoked, pending, percentComplete |
| `CampaignHistoryDto` | id, oldStatus, newStatus, changedByUsername, changedAt, note |

---

## 7. Backend: Controller

### `AccessReviewController`

**File:** `src/main/java/com/ldapadmin/controller/directory/AccessReviewController.java`

**Base path:** `/api/v1/directories/{directoryId}/access-reviews`

| Method | Path | Description | Feature Key |
|--------|------|-------------|-------------|
| `GET` | `/` | List campaigns (paginated) | `access_review.manage` |
| `POST` | `/` | Create campaign (DRAFT) | `access_review.manage` |
| `GET` | `/{campaignId}` | Get campaign detail + progress | `access_review.manage` or assigned reviewer |
| `POST` | `/{campaignId}/activate` | Activate → snapshot members, notify | `access_review.manage` |
| `POST` | `/{campaignId}/close` | Close campaign (optional `?force=true`) | `access_review.manage` |
| `POST` | `/{campaignId}/cancel` | Cancel campaign | `access_review.manage` |
| `GET` | `/{campaignId}/groups` | List review groups for campaign | `access_review.review` |
| `GET` | `/{campaignId}/groups/{groupId}/decisions` | List member decisions for a group | `access_review.review` |
| `POST` | `/{campaignId}/groups/{groupId}/decisions/{decisionId}` | Submit single decision | `access_review.review` |
| `POST` | `/{campaignId}/groups/{groupId}/decisions/bulk` | Submit bulk decisions | `access_review.review` |
| `GET` | `/{campaignId}/export` | Export decisions as CSV/PDF (`?format=csv` or `?format=pdf`) | `access_review.manage` |
| `GET` | `/{campaignId}/history` | Get campaign status transition history | `access_review.manage` |

Pattern follows `ScheduledReportJobController`: `@DirectoryId` on `directoryId`, `@RequiresFeature` on each method, `@AuthenticationPrincipal AuthPrincipal`.

---

## 8. Backend: Campaign Export

The export endpoint supports two formats controlled by a `?format=` query parameter:

**CSV** (default) — columns:
```
Campaign, Group DN, Group Name, Member DN, Member Display, Decision, Decided By, Comment, Decided At, Revoked At
```

**PDF** — formatted report with campaign summary header (name, status, deadline, progress stats), followed by decisions grouped by review group. Reuses patterns from `ReportExecutionService` for PDF generation.

Both formats reuse the existing generation patterns from `BulkUserService` / `ReportExecutionService`. The export serves as compliance evidence for SOX, SOC 2, and ISO 27001 audits.

---

## 9. Frontend: API Module

**File:** `frontend/src/api/accessReviews.js`

```javascript
import client from './client'

const base = (dirId) => `/directories/${dirId}/access-reviews`

export const listCampaigns = (dirId, params) =>
  client.get(base(dirId), { params })

export const createCampaign = (dirId, data) =>
  client.post(base(dirId), data)

export const getCampaign = (dirId, campaignId) =>
  client.get(`${base(dirId)}/${campaignId}`)

export const activateCampaign = (dirId, campaignId) =>
  client.post(`${base(dirId)}/${campaignId}/activate`)

export const closeCampaign = (dirId, campaignId, force = false) =>
  client.post(`${base(dirId)}/${campaignId}/close`, null, { params: { force } })

export const cancelCampaign = (dirId, campaignId) =>
  client.post(`${base(dirId)}/${campaignId}/cancel`)

export const listReviewGroups = (dirId, campaignId) =>
  client.get(`${base(dirId)}/${campaignId}/groups`)

export const listDecisions = (dirId, campaignId, groupId) =>
  client.get(`${base(dirId)}/${campaignId}/groups/${groupId}/decisions`)

export const submitDecision = (dirId, campaignId, groupId, decisionId, data) =>
  client.post(`${base(dirId)}/${campaignId}/groups/${groupId}/decisions/${decisionId}`, data)

export const bulkDecide = (dirId, campaignId, groupId, items) =>
  client.post(`${base(dirId)}/${campaignId}/groups/${groupId}/decisions/bulk`, { items })

export const exportCampaign = (dirId, campaignId, format = 'csv') =>
  client.get(`${base(dirId)}/${campaignId}/export`, { params: { format }, responseType: 'blob' })

export const getCampaignHistory = (dirId, campaignId) =>
  client.get(`${base(dirId)}/${campaignId}/history`)
```

---

## 10. Frontend: Views

### `CampaignListView.vue`

**File:** `frontend/src/views/accessReviews/CampaignListView.vue`

- Table of campaigns with columns: Name, Status (badge), Deadline, Progress bar, Created By, Actions
- "New Campaign" button → create dialog
- Status filter tabs: All / Draft / Active / Closed
- Click row → campaign detail

### `CampaignCreateView.vue`

**File:** `frontend/src/views/accessReviews/CampaignCreateView.vue`

- Form: name, description, starts-at picker (optional), deadline picker, auto-revoke toggle, auto-revoke-on-expiry toggle
- Group selector: `DnPicker` to select group DNs (reuse existing component)
- For each group: assign a reviewer (dropdown of admin accounts)
- Member attribute selector per group (member / uniqueMember / memberUid)
- Submit → creates DRAFT campaign

### `CampaignDetailView.vue`

**File:** `frontend/src/views/accessReviews/CampaignDetailView.vue`

- Campaign header: name, status badge, deadline (with countdown), starts-at date, description
- Progress summary: donut chart or progress bar showing confirmed/revoked/pending
- Table of review groups: Group Name, Reviewer, Progress, decision counts
- Action buttons: Activate (if DRAFT), Close (if ACTIVE), Cancel, Export (CSV/PDF dropdown)
- Status history timeline — collapsible section showing all status transitions with timestamps, actors, and notes
- Close confirmation dialog warns about undecided items when force-closing

### `ReviewDecisionsView.vue`

**File:** `frontend/src/views/accessReviews/ReviewDecisionsView.vue`

- Table of members for a single review group
- Columns: Member DN, Display Name, Decision (CONFIRM/REVOKE/Pending), Comment, Decided At
- Inline decision buttons: Confirm (green check) / Revoke (red X)
- Bulk action toolbar: "Confirm All Remaining", "Select and Revoke"
- Comment field (optional, shown on expand or inline)
- Filter: All / Pending / Confirmed / Revoked

---

## 11. Frontend: Router

**File:** `frontend/src/router/index.js`

Add routes under the app shell children:

```javascript
// Access Reviews
{
  path: 'directories/:dirId/access-reviews',
  name: 'accessReviews',
  component: () => import('@/views/accessReviews/CampaignListView.vue'),
},
{
  path: 'directories/:dirId/access-reviews/new',
  name: 'accessReviewCreate',
  component: () => import('@/views/accessReviews/CampaignCreateView.vue'),
},
{
  path: 'directories/:dirId/access-reviews/:campaignId',
  name: 'accessReviewDetail',
  component: () => import('@/views/accessReviews/CampaignDetailView.vue'),
},
{
  path: 'directories/:dirId/access-reviews/:campaignId/groups/:groupId',
  name: 'accessReviewDecisions',
  component: () => import('@/views/accessReviews/ReviewDecisionsView.vue'),
},
```

Add navigation link in `AppLayout.vue` sidebar under the directory section, alongside Approvals and Reports.

---

## 12. Implementation Order

1. **Database migration** (`V20`) — tables (campaigns, groups, decisions, campaign_history) + feature key constraint update
2. **Enums** — `CampaignStatus` (incl. EXPIRED), `ReviewDecision`, add to `FeatureKey` and `AuditAction`
3. **Entities** — `AccessReviewCampaign`, `AccessReviewGroup`, `AccessReviewDecision`, `AccessReviewCampaignHistory`
4. **Repositories** — four JPA repositories (incl. history)
5. **DTOs** — request/response records (incl. `CampaignHistoryDto`)
6. **Services** — `AccessReviewCampaignService`, `AccessReviewDecisionService`, `AccessReviewNotificationService`
7. **Scheduler** — `AccessReviewScheduler` (expiry processing + deadline reminders)
8. **Controller** — `AccessReviewController` (incl. history + PDF export endpoints)
9. **Service tests** — unit tests for campaign lifecycle (create → activate → decide → close → expire)
10. **Scheduler tests** — unit tests for expiry processing and reminder logic
11. **Controller tests** — MockMvc tests following `BaseControllerTest` pattern
12. **Frontend API module** — `accessReviews.js`
13. **Frontend views** — list → create → detail (with history timeline) → decisions
14. **Router + navigation** — wire up routes and sidebar link

---

## Files Summary

| Layer | Files |
|-------|-------|
| Migration | `src/main/resources/db/migration/V20__access_review_campaigns.sql` |
| Enums | `entity/enums/CampaignStatus.java`, `entity/enums/ReviewDecision.java`, update `FeatureKey.java`, `AuditAction.java` |
| Entities | `entity/AccessReviewCampaign.java`, `entity/AccessReviewGroup.java`, `entity/AccessReviewDecision.java`, `entity/AccessReviewCampaignHistory.java` |
| Repos | `repository/AccessReviewCampaignRepository.java`, `AccessReviewGroupRepository.java`, `AccessReviewDecisionRepository.java`, `AccessReviewCampaignHistoryRepository.java` |
| DTOs | `dto/accessreview/CreateCampaignRequest.java`, `CampaignSummaryDto.java`, `CampaignDetailDto.java`, `ReviewGroupDto.java`, `DecisionDto.java`, `SubmitDecisionRequest.java`, `BulkDecisionRequest.java`, `CampaignProgressDto.java`, `CampaignHistoryDto.java` |
| Services | `service/AccessReviewCampaignService.java`, `service/AccessReviewDecisionService.java`, `service/AccessReviewNotificationService.java`, `service/AccessReviewScheduler.java` |
| Controller | `controller/directory/AccessReviewController.java` |
| Tests | `service/AccessReviewCampaignServiceTest.java`, `service/AccessReviewDecisionServiceTest.java`, `service/AccessReviewSchedulerTest.java`, `controller/AccessReviewControllerTest.java` |
| Frontend | `api/accessReviews.js`, `views/accessReviews/CampaignListView.vue`, `CampaignCreateView.vue`, `CampaignDetailView.vue`, `ReviewDecisionsView.vue` |
| Router | Update `router/index.js`, `components/AppLayout.vue` |

---

## Security Considerations

| Risk | Mitigation |
|------|------------|
| Reviewer sees groups they shouldn't | Controller checks that the authenticated user is the assigned reviewer for the group, or has `access_review.manage` permission |
| Auto-revoke removes critical memberships | Default `auto_revoke = false` and `auto_revoke_on_expiry = false`; activation confirmation dialog warns about auto-revoke; audit trail records every removal |
| Campaign deadline gaming | Deadline is server-enforced; scheduler auto-expires overdue campaigns; decisions after expiry are rejected (status must be ACTIVE) |
| Mass revocation | Close action requires explicit confirmation; force-close requires separate `force=true` parameter |
| Stale member snapshot | Members are snapshotted at activation time; campaign detail view can show a "members changed since snapshot" warning by comparing against live LDAP |

---

## Future Enhancements (not in scope)

- **Recurring campaigns** — auto-create a new campaign on a schedule (quarterly recertification)
- **Delegation** — reviewer delegates their assignment to another admin
- **Manager-based auto-assignment** — assign reviewer based on the `manager` attribute of group members
- **Dashboard widget** — show active campaigns and pending reviews on a future dashboard
- **Stale member detection** — compare snapshot against live LDAP and highlight members added/removed since activation
