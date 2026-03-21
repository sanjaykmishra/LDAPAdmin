# Approval Workflow for User Creation

## Overview
Add an approval workflow so that user creation (individual + bulk import) requires
approval by a designated approver before the LDAP entry is actually created.
The workflow is configurable per-realm.

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

Initial key: `approval.user_create.enabled` → `true` / `false`

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

### New feature key
Add `APPROVAL_MANAGE` to the `FeatureKey` enum and DB constraint — grants the
ability to approve/reject pending requests (designated approver permission).

Update `admin_feature_permissions` constraint to include `'approval.manage'`.

---

## 2. Backend: New Entities

### `RealmSetting` entity
- Fields: `id`, `realm` (ManyToOne), `key`, `value`
- Repository: `RealmSettingRepository` with `findByRealmIdAndKey()`

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

### `ApprovalWorkflowService`
```java
@Service
public class ApprovalWorkflowService {
    // Check if approval is required for a realm
    boolean isApprovalRequired(UUID realmId);

    // Submit a user-create request for approval (returns PendingApproval)
    PendingApproval submitForApproval(UUID directoryId, UUID realmId,
        AuthPrincipal requester, ApprovalRequestType type, Object payload);

    // List pending approvals (filtered by realms the caller can approve)
    List<PendingApproval> listPending(UUID directoryId, AuthPrincipal principal);

    // Approve: creates the LDAP entry from the stored payload
    PendingApproval approve(UUID approvalId, AuthPrincipal approver);

    // Reject with reason
    PendingApproval reject(UUID approvalId, AuthPrincipal approver, String reason);
}
```

**Approve flow:**
1. Load the `PendingApproval` record
2. Verify approver ≠ requester
3. Verify approver has `APPROVAL_MANAGE` feature permission
4. Deserialise the payload
5. Execute the LDAP create via `LdapOperationService`
6. Update status → APPROVED, set `reviewedBy` and `reviewedAt`
7. Audit the action

### Integration with existing user creation

Modify `UserController.createUser()`:
- Before creating, check `approvalWorkflowService.isApprovalRequired(realmId)`
- If required: call `submitForApproval()` and return **202 Accepted** with the
  pending approval ID instead of creating immediately
- If not required: proceed as before (201 Created)

Modify `BulkUserController.importUsers()`:
- Same check per realm
- If required: store the entire import request + CSV as the payload, return 202
- If not required: proceed as before

### `RealmSettingService`
- `getSetting(realmId, key)` → Optional<String>
- `setSetting(realmId, key, value)`
- `isApprovalRequired(realmId)` → boolean (convenience)

---

## 4. Backend: Controller

### `ApprovalController` — under `/api/v1/directories/{directoryId}/approvals`

| Method | Path | Description | Permission |
|--------|------|-------------|------------|
| GET    | `/`  | List pending approvals for this directory | APPROVAL_MANAGE |
| GET    | `/{id}` | Get approval details | APPROVAL_MANAGE |
| POST   | `/{id}/approve` | Approve a pending request | APPROVAL_MANAGE |
| POST   | `/{id}/reject` | Reject with reason body | APPROVAL_MANAGE |

### `RealmSettingController` — extend existing RealmController

| Method | Path | Description | Permission |
|--------|------|-------------|------------|
| GET    | `/realms/{realmId}/settings` | Get realm settings | SUPERADMIN |
| PUT    | `/realms/{realmId}/settings` | Update realm settings | SUPERADMIN |

---

## 5. Frontend Changes

### New API module: `frontend/src/api/approvals.js`
```js
export const listPendingApprovals = (dirId) => client.get(`/directories/${dirId}/approvals`)
export const getApproval = (dirId, id) => client.get(`/directories/${dirId}/approvals/${id}`)
export const approveRequest = (dirId, id) => client.post(`/directories/${dirId}/approvals/${id}/approve`)
export const rejectRequest = (dirId, id, reason) => client.post(`/directories/${dirId}/approvals/${id}/reject`, { reason })
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

### Navigation
- Add "Pending Approvals" nav item (visible when user has APPROVAL_MANAGE)
- Show badge with pending count

---

## 6. Implementation Order

1. **Database migration** — new tables + feature key
2. **Entities + Repositories** — RealmSetting, PendingApproval
3. **RealmSettingService** — per-realm config
4. **ApprovalWorkflowService** — core logic
5. **ApprovalController** — REST endpoints
6. **Modify UserController** — intercept creates when approval required
7. **Modify BulkUserController** — intercept imports when approval required
8. **Frontend API + PendingApprovalsView** — approval UI
9. **Frontend user create handling** — handle 202 responses
10. **Realm settings UI** — toggle in realm edit form
11. **Tests** — service + controller tests
