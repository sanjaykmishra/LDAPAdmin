# Approval Workflow for User Creation

## Overview
Add an approval workflow so that user creation (individual + bulk import) requires
approval by a designated approver before the LDAP entry is actually created.
The workflow is configurable per-realm, including which users are approvers.
Email notifications are sent to approvers when new requests are submitted and
to requesters when their requests are approved or rejected.

**Key design decision — approver designation:**

Approver designation is part of realm configuration (not a feature permission).
The mechanism depends on whether LDAP authentication is enabled for the
application:

- **LDAP auth enabled:** Approvers for a realm are determined by membership in
  a configurable LDAP group. The group DN is stored as a realm setting
  (`approval.approver_group_dn`). Group membership is checked at runtime against
  the LDAP auth server (the same server used for admin login, configured in
  `application_settings`). The admin's `ldapDn` is matched against the group's
  `member` / `uniqueMember` / `memberUid` attributes.

- **LDAP auth not enabled (LOCAL/OIDC only):** Approvers are designated
  explicitly via the `realm_approvers` table, managed by a superadmin.

This dual approach lets organisations with a central LDAP directory manage
approver membership in their existing directory (group changes take effect
immediately without touching LDAPAdmin config), while deployments without
LDAP auth can still use the feature with manual assignment.

---

## 1. Database Changes (new Flyway migration)

### New table: `realm_settings`
Stores per-realm configuration flags.

```sql
CREATE TABLE realm_settings (
    id        UUID        NOT NULL DEFAULT gen_random_uuid(),
    realm_id  UUID        NOT NULL,
    key       VARCHAR(100) NOT NULL,
    value     VARCHAR(500) NOT NULL,
    CONSTRAINT pk_realm_settings PRIMARY KEY (id),
    CONSTRAINT uq_realm_setting  UNIQUE (realm_id, key),
    CONSTRAINT fk_rs_realm FOREIGN KEY (realm_id) REFERENCES realms (id) ON DELETE CASCADE
);
```

Settings keys used by this feature:

| Key | Values | Description |
|-----|--------|-------------|
| `approval.user_create.enabled` | `true` / `false` | Whether approval is required for user creation in this realm |
| `approval.approver_group_dn` | LDAP group DN | DN of the LDAP group whose members are approvers (LDAP auth mode only) |

### New table: `realm_approvers`
Fallback approver designation for non-LDAP auth deployments. Used only when
LDAP auth is **not** enabled in `application_settings.enabled_auth_types`.

```sql
CREATE TABLE realm_approvers (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    realm_id        UUID NOT NULL,
    admin_account_id UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_realm_approvers PRIMARY KEY (id),
    CONSTRAINT uq_realm_approver UNIQUE (realm_id, admin_account_id),
    CONSTRAINT fk_ra_realm FOREIGN KEY (realm_id) REFERENCES realms (id) ON DELETE CASCADE,
    CONSTRAINT fk_ra_admin FOREIGN KEY (admin_account_id) REFERENCES accounts (id) ON DELETE CASCADE
);
```

### New table: `pending_approvals`
Stores pending user creation requests awaiting approval.

```sql
CREATE TABLE pending_approvals (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    directory_id    UUID         NOT NULL,
    realm_id        UUID         NOT NULL,
    requested_by    UUID         NOT NULL,   -- account.id of requester
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED
    request_type    VARCHAR(30)  NOT NULL,   -- USER_CREATE, BULK_IMPORT
    payload         JSONB        NOT NULL,   -- serialised create request or bulk import data
    reject_reason   TEXT,
    reviewed_by     UUID,                    -- account.id of approver/rejecter
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reviewed_at     TIMESTAMPTZ,
    CONSTRAINT pk_pending_approvals PRIMARY KEY (id),
    CONSTRAINT fk_pa_directory FOREIGN KEY (directory_id) REFERENCES directory_connections (id) ON DELETE CASCADE,
    CONSTRAINT fk_pa_realm     FOREIGN KEY (realm_id)     REFERENCES realms (id) ON DELETE CASCADE,
    CONSTRAINT fk_pa_requester FOREIGN KEY (requested_by) REFERENCES accounts (id),
    CONSTRAINT fk_pa_reviewer  FOREIGN KEY (reviewed_by)  REFERENCES accounts (id),
    CONSTRAINT chk_pa_status   CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_pa_realm_status ON pending_approvals (realm_id, status);
```

**Note:** No new `FeatureKey` is needed. Approval rights are determined by
LDAP group membership (when LDAP auth is enabled) or by the `realm_approvers`
table (when LDAP auth is not enabled).

---

## 2. Backend: New Entities

### `RealmSetting` entity
- Fields: `id`, `realm` (ManyToOne), `key`, `value`
- Repository: `RealmSettingRepository` with `findByRealmIdAndKey()`

### `RealmApprover` entity
- Fields: `id`, `realm` (ManyToOne), `adminAccount` (ManyToOne), `createdAt`
- Repository: `RealmApproverRepository`
  - `findByRealmId(realmId)` → list of approvers for a realm
  - `findByAdminAccountId(accountId)` → realms where user is approver
  - `existsByRealmIdAndAdminAccountId(realmId, accountId)` → check if user is approver

### `PendingApproval` entity
- Fields: `id`, `directoryId`, `realmId`, `requestedBy`, `status` (enum),
  `requestType` (enum), `payload` (JSON string), `rejectReason`,
  `reviewedBy`, `createdAt`, `reviewedAt`
- Enums: `ApprovalStatus { PENDING, APPROVED, REJECTED }`,
  `ApprovalRequestType { USER_CREATE, BULK_IMPORT }`
- Repository: `PendingApprovalRepository`
  - `findByRealmIdAndStatus(realmId, status)`
  - `findByDirectoryIdAndStatus(directoryId, status)`
  - `countByRealmIdAndStatus(realmId, PENDING)` (for badge counts)

---

## 3. Backend: Service Layer

### `RealmSettingService`
- `getSetting(realmId, key)` → Optional<String>
- `setSetting(realmId, key, value)`
- `isApprovalRequired(realmId)` → boolean (convenience)
- `getApproverGroupDn(realmId)` → Optional<String> (convenience)

### `RealmApproverService`

Unified service that abstracts the two approver resolution strategies. The
service checks `ApplicationSettingsService` to determine which strategy is
active.

```java
@Service
public class RealmApproverService {

    // Determine if the caller is an approver for the given realm.
    // - LDAP auth enabled: opens a one-shot connection to the LDAP auth server,
    //   reads the group DN from realm setting `approval.approver_group_dn`,
    //   and checks whether the account's ldapDn is a member of that group.
    // - LDAP auth not enabled: checks the realm_approvers table.
    boolean isApprover(UUID realmId, UUID accountId);

    // Return the list of approver accounts for a realm.
    // - LDAP auth enabled: reads the LDAP group membership and matches DNs
    //   against accounts with authType=LDAP. Returns only accounts that
    //   exist in the accounts table (i.e. provisioned admins).
    // - LDAP auth not enabled: returns accounts from realm_approvers table.
    List<Account> getApprovers(UUID realmId);

    // Return the realms where the given account is an approver.
    // - LDAP auth enabled: iterates realms that have an approver_group_dn
    //   configured and checks membership for each.
    // - LDAP auth not enabled: queries realm_approvers table.
    List<Realm> getRealmsWhereApprover(UUID accountId);

    // Manual approver management (only used when LDAP auth is not enabled).
    // Throws IllegalStateException if LDAP auth is enabled.
    void setApprovers(UUID realmId, List<UUID> accountIds);
}
```

**LDAP group membership check implementation:**

```java
private boolean isLdapGroupMember(String groupDn, String memberDn) {
    ApplicationSettings settings = appSettingsService.get();
    // Open one-shot connection to LDAP auth server using:
    //   settings.getLdapAuthHost(), getLdapAuthPort(), getLdapAuthSslMode(),
    //   getLdapAuthBindDn(), getLdapAuthBindPasswordEnc() (decrypted)
    // Read the group entry at groupDn
    // Check member/uniqueMember attributes for memberDn match
    // Also check memberUid for uid-based match (extract uid from memberDn)
    // Return true if found
}
```

- Uses the same LDAP auth server connection config that `AuthenticationService`
  uses for admin login — no additional LDAP connection configuration needed
- Binds with the service account (`ldapAuthBindDn` / `ldapAuthBindPasswordEnc`)
  to read group membership, since the auth server may not allow anonymous reads
- Connection is one-shot (not pooled) — approval checks are infrequent

### `ApprovalWorkflowService`
```java
@Service
public class ApprovalWorkflowService {
    // Check if approval is required for a realm
    boolean isApprovalRequired(UUID realmId);

    // Submit a user-create request for approval (returns PendingApproval)
    PendingApproval submitForApproval(UUID directoryId, UUID realmId,
        AuthPrincipal requester, ApprovalRequestType type, Object payload);

    // List pending approvals (filtered by realms where caller is an approver)
    List<PendingApproval> listPending(UUID directoryId, AuthPrincipal principal);

    // Approve: creates the LDAP entry from the stored payload
    PendingApproval approve(UUID approvalId, AuthPrincipal approver);

    // Reject with reason
    PendingApproval reject(UUID approvalId, AuthPrincipal approver, String reason);
}
```

**Approve flow:**
1. Load the `PendingApproval` record
2. Verify approver ≠ requester (optional policy — configurable)
3. Verify approver via `realmApproverService.isApprover()` (delegates to
   LDAP group check or table check depending on auth mode)
4. Deserialise the payload
5. Execute the LDAP create via `LdapOperationService`
6. Update status → APPROVED, set `reviewedBy` and `reviewedAt`
7. Audit the action
8. Send approval notification email to requester

**Reject flow:**
1. Load the `PendingApproval` record
2. Verify approver via `realmApproverService.isApprover()`
3. Update status → REJECTED, set `rejectReason`, `reviewedBy`, `reviewedAt`
4. Audit the action
5. Send rejection notification email to requester

### `ApprovalNotificationService`
Sends email notifications related to the approval workflow using the existing
SMTP configuration in `application_settings`.

```java
@Service
public class ApprovalNotificationService {

    // Notify all approvers for a realm that a new request is pending
    void notifyApproversOfNewRequest(PendingApproval approval);

    // Notify the requester that their request was approved
    void notifyRequesterApproved(PendingApproval approval);

    // Notify the requester that their request was rejected
    void notifyRequesterRejected(PendingApproval approval);

    // Send reminder emails to approvers for requests that have been
    // pending longer than a configurable threshold
    void sendPendingReminders();
}
```

**Email details:**

| Event | Recipients | Subject | Body |
|-------|-----------|---------|------|
| New request submitted | All approvers for the realm | "[LDAPAdmin] New approval request pending — {realm name}" | Request type, requester, summary of payload, link to review |
| Request approved | Requester | "[LDAPAdmin] Your request was approved — {realm name}" | What was approved, who approved, timestamp |
| Request rejected | Requester | "[LDAPAdmin] Your request was rejected — {realm name}" | What was rejected, who rejected, reason |
| Pending reminder | All approvers for the realm | "[LDAPAdmin] Reminder: {count} pending approvals — {realm name}" | Count of pending requests, link to review |

**Implementation notes:**
- Uses the existing SMTP configuration already in the `application_settings`
  table and `ApplicationSettings` entity (`smtpHost`, `smtpPort`,
  `smtpSenderAddress`, `smtpUsername`, `smtpPasswordEncrypted`, `smtpUseTls`)
  — no new config fields needed
- Email sending is `@Async` so it does not block the approval workflow
- Uses the existing `email` column on the `accounts` table for recipient addresses
- If SMTP is not configured, log a warning and skip email sending gracefully
- Pending reminders are triggered by a `@Scheduled` cron (configurable,
  default: daily at 9:00 AM)
- `notifyApproversOfNewRequest()` calls `realmApproverService.getApprovers()`
  to resolve the recipient list (works for both LDAP and table-based modes)

### Integration with existing user creation

Modify `UserController.createUser()`:
- Before creating, check `approvalWorkflowService.isApprovalRequired(realmId)`
- If required: call `submitForApproval()` and return **202 Accepted** with the
  pending approval ID instead of creating immediately
- If not required: proceed as before (201 Created)
- On submit, call `approvalNotificationService.notifyApproversOfNewRequest()`

Modify `BulkUserController.importUsers()`:
- Same check per realm
- If required: store the entire import request + CSV as the payload, return 202
- If not required: proceed as before
- On submit, call `approvalNotificationService.notifyApproversOfNewRequest()`

---

## 4. Backend: Controller

### `ApprovalController` — under `/api/v1/directories/{directoryId}/approvals`

| Method | Path | Description | Access Check |
|--------|------|-------------|--------------|
| GET    | `/`  | List pending approvals for this directory | Caller must be approver for at least one realm in the directory |
| GET    | `/{id}` | Get approval details | Caller must be approver for the approval's realm |
| POST   | `/{id}/approve` | Approve a pending request | Caller must be approver for the approval's realm |
| POST   | `/{id}/reject` | Reject with reason body | Caller must be approver for the approval's realm |

Access is enforced by checking `realmApproverService.isApprover(realmId, principal.getAccountId())`
in each endpoint. This delegates to the correct strategy (LDAP group or table)
automatically.

### `RealmSettingController` — extend existing RealmController

| Method | Path | Description | Permission |
|--------|------|-------------|------------|
| GET    | `/realms/{realmId}/settings` | Get realm settings | SUPERADMIN |
| PUT    | `/realms/{realmId}/settings` | Update realm settings | SUPERADMIN |

### Approver management — extend existing RealmController

These endpoints are only active when LDAP auth is **not** enabled. When LDAP
auth is enabled, approver management is done in the LDAP directory itself
(managing group membership), so these endpoints return **409 Conflict** with a
message indicating that approvers are managed via LDAP group membership.

| Method | Path | Description | Permission |
|--------|------|-------------|------------|
| GET    | `/realms/{realmId}/approvers` | List approvers for realm | SUPERADMIN |
| PUT    | `/realms/{realmId}/approvers` | Set approvers for realm (list of account IDs) | SUPERADMIN |

---

## 5. Frontend Changes

### New API module: `frontend/src/api/approvals.js`
```js
export const listPendingApprovals = (dirId) =>
    client.get(`/directories/${dirId}/approvals`)

export const getApproval = (dirId, id) =>
    client.get(`/directories/${dirId}/approvals/${id}`)

export const approveRequest = (dirId, id) =>
    client.post(`/directories/${dirId}/approvals/${id}/approve`)

export const rejectRequest = (dirId, id, reason) =>
    client.post(`/directories/${dirId}/approvals/${id}/reject`, { reason })
```

### New view: `PendingApprovalsView.vue`
- Table of pending requests with: requester, type, created date, summary
- Click to expand/view full payload details
- Approve / Reject buttons with confirmation
- Badge count on nav item showing pending count

### Modify user creation flow
- Handle 202 response from create user → show "Submitted for approval" notification
- Handle 202 from bulk import → show "Import submitted for approval" notification

### Realm settings UI
- Add toggle in realm edit form: "Require approval for user creation"
- **LDAP auth enabled:** Show a text input for the approver group DN
  (`approval.approver_group_dn`). Optionally add a "Browse" button that lets
  the superadmin pick a group from the LDAP auth server. Display the current
  group members (read-only) resolved from the LDAP group for verification.
- **LDAP auth not enabled:** Show a multi-select to pick approvers from the
  list of admin accounts that have access to this realm (i.e. have an
  `AdminRealmRole` row for this realm). Show selected approvers with their
  username and email.

### Navigation
- Add "Pending Approvals" nav item (visible when user is an approver for any realm)
- Show badge with pending count

---

## 6. Implementation Order

1. **Database migration** — new tables (`realm_settings`, `realm_approvers`,
   `pending_approvals`)
2. **Entities + Repositories** — `RealmSetting`, `RealmApprover`, `PendingApproval`
3. **RealmSettingService** — per-realm config (including `approver_group_dn`)
4. **RealmApproverService** — dual-mode approver resolution (LDAP group / table)
5. **ApprovalWorkflowService** — core approval logic
6. **ApprovalNotificationService** — email notifications
7. **ApprovalController** — REST endpoints for approvals
8. **Realm approver endpoints** — add approver management to RealmController
   (with 409 guard when LDAP auth is enabled)
9. **Modify UserController** — intercept creates when approval required
10. **Modify BulkUserController** — intercept imports when approval required
11. **Frontend API + PendingApprovalsView** — approval UI
12. **Frontend user create handling** — handle 202 responses
13. **Realm settings UI** — toggle + approver group DN (LDAP) or approver
    picker (non-LDAP) in realm edit form
14. **Scheduled reminder emails** — cron job for pending approval reminders
15. **Tests** — service + controller tests (both LDAP and table-based modes)
