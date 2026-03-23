# Independent Authorization Model Review

**Date:** 2026-03-23
**Scope:** Full codebase review — SecurityConfig, JWT, all 22 controllers, PermissionService, ApprovalWorkflowService, SelfServiceService
**Method:** Direct code reading; no reliance on prior analysis documents

---

## 1. Architecture Overview

### Authentication

- **Mechanism:** Stateless JWT (HMAC-SHA-256) via httpOnly cookie (`SameSite=Strict`) with `Authorization: Bearer` header fallback.
- **Principal types:** `SUPERADMIN`, `ADMIN`, `SELF_SERVICE` — encoded as an immutable claim (`type`) in the JWT.
- **Token claims:** `sub` (username), `aid` (account UUID), `type`, `iat`, `exp`. Self-service tokens additionally carry `dn` and `did` (directory ID).
- **Session:** Fully stateless (`SessionCreationPolicy.STATELESS`). No server-side session, no CSRF (justified by `SameSite=Strict` cookie).

### Authorization Layers (outer → inner)

| Layer | Mechanism | Scope |
|-------|-----------|-------|
| **L1** | `SecurityConfig` HTTP filter chain | URL-pattern + role match (SUPERADMIN, ADMIN, SELF_SERVICE) |
| **L2** | `@PreAuthorize` (Spring method security) | SpEL expressions on controller methods |
| **L3** | `@RequiresFeature` + `FeaturePermissionAspect` | Directory-scoped, 3-dimension check (profile access + base role + feature override) |
| **L4** | Programmatic service-layer checks | `PermissionService.requireProfileAccess()`, `canViewApproval()`, etc. |

### Permission Dimensions (for `@RequiresFeature`)

1. **Profile access** — admin must have a row in `admin_profile_roles` for the target profile (or any profile in the directory)
2. **Base role** — `ADMIN` grants all features by default; `READ_ONLY` grants only `BULK_EXPORT` and `REPORTS_RUN`
3. **Feature override** — per-admin, per-feature explicit enable/disable in `admin_feature_permissions`, overriding base-role defaults

Superadmins bypass all three dimensions unconditionally.

---

## 2. Strengths

### S1. Immutable JWT principal
The `AuthPrincipal` is a Java `record` — fields cannot be mutated after construction. The role (`type`) is embedded in the signed JWT and cannot be escalated at runtime. There is no role-lookup at request time that could be manipulated.

### S2. Well-layered defense-in-depth for write operations
Write endpoints that go through `@RequiresFeature` get three checks: directory membership, base-role authorization, and feature-key gating. On top of that, provisioning operations (user create, bulk import, user move, group member add) pass through the approval workflow, which independently verifies profile access for both the requester and the approver.

### S3. Strong approval workflow integrity
- Self-approval prevention (requester cannot approve own request)
- Requester cannot edit their own pending payload
- Payload schema validation on edit (type-specific deserialization)
- `@Version` optimistic locking on `PendingApproval` prevents concurrent approval race conditions
- Unprovisioned OUs (DNs not matching any profile) are forced into directory-level approval rather than skipping approval
- Approver's profile access is re-verified at execution time, not just at approval time
- Password attributes are obfuscated in audit trails

### S4. Clean superadmin isolation
All superadmin management endpoints (`/api/v1/superadmin/**`) are gated by both the HTTP filter chain (`hasRole('SUPERADMIN')`) and class-level `@PreAuthorize("hasRole('SUPERADMIN')")`. This is double-locked.

### S5. Properly scoped JWT cookies
`httpOnly`, `Secure` (configurable), `SameSite=Strict`, scoped to `/api/v1`. CSRF is disabled but justified by the SameSite policy.

### S6. LDAP filter injection prevention
`AuthController.selfServiceLogin()` uses `escapeLdapFilter()` (RFC 4515 escaping) before constructing the LDAP search filter. This prevents LDAP injection through the username field.

### S7. Consistent audit trail
Approval submissions, approvals, rejections, payload edits, and auto-approvals all generate audit events with type, payload, and actor information.

### S8. Rate limiting on sensitive endpoints
Login (admin + self-service + OIDC), bulk import/export, and report execution are rate-limited.

---

## 3. Weaknesses

### W1. Three competing authorization mechanisms with no enforced convention

**Severity: Medium (architectural)**

The codebase uses `@PreAuthorize`, `@RequiresFeature`, and "HTTP filter only" as three distinct authorization approaches. There is no compile-time or startup-time enforcement that every endpoint uses one. This creates cognitive burden and makes it easy for new endpoints to ship with only the permissive L1 catch-all.

| Mechanism | Controllers using it |
|-----------|---------------------|
| `@RequiresFeature` only | BulkUser, BulkGroup, ScheduledReport |
| `@PreAuthorize` only | 6 superadmin controllers, LifecyclePlaybook, ProvisioningProfile |
| Neither (L1 catch-all only) | **Approval, CsvMappingTemplate, DirectoryBrowse, Schema** |
| Mixed | AccessReview (both), User (feature + service), Group (feature + service) |

**Impact:** A developer adding a new controller under `/api/v1/directories/` gets ADMIN+SUPERADMIN access "for free" from the catch-all without needing to add any annotation. There's no guardrail to flag the omission.

### W2. Read endpoints systematically unprotected by feature checks

**Severity: Medium**

Read operations across most directory-scoped controllers rely solely on the L1 catch-all (`hasAnyRole('ADMIN', 'SUPERADMIN')`). Any admin with access to *any* directory can call these endpoints against *any* directoryId:

| Endpoint | What it exposes |
|----------|----------------|
| `GET /directories/{id}/users` (search) | Full user search across any directory |
| `GET /directories/{id}/users/entry` | Individual user attributes |
| `GET /directories/{id}/users/password-status` | Password expiry metadata |
| `GET /directories/{id}/groups` | Group listing |
| `GET /directories/{id}/groups/entry` | Group attributes |
| `GET /directories/{id}/groups/members` | Group membership |
| `GET /directories/{id}/browse` | DIT browsing |
| `GET /directories/{id}/schema/**` | Schema discovery (5 endpoints) |
| `GET /directories/{id}/approvals` | Pending approval list (service-filtered) |
| `GET /directories/{id}/csv-templates` | CSV template list |

The `@RequiresFeature` aspect calls `requireDirectoryAccess()` which checks that the admin has a profile role in the directory. But endpoints *without* `@RequiresFeature` never call `requireDirectoryAccess()`. An admin assigned to Directory A can query users, groups, and browse the DIT of Directory B.

**Exception:** Approval listing is filtered at the service layer (`listPending` filters by `canViewApproval`), so the data returned is scoped — but the endpoint itself is callable.

### W3. `countPending` leaks cross-directory approval counts

**Severity: Low**

`ApprovalController.countPending()` at line 66-71:
```java
public Map<String, Long> countPending(@DirectoryId @PathVariable UUID directoryId, ...) {
    return Map.of("pending", service.countPending(directoryId));
}
```

`ApprovalWorkflowService.countPending()` at line 362-365:
```java
public long countPending(UUID directoryId) {
    return approvalRepo.countByDirectoryIdAndStatus(directoryId, ApprovalStatus.PENDING);
}
```

The principal is passed to the controller but **never checked** in `countPending()`. Any admin can query the pending approval count for any directory, even ones they have no access to. While this is a minor information leak (just a count), it violates the principle that directory-scoped data should be directory-scoped.

### W4. CsvMappingTemplate write operations lack feature or directory-scoping checks

**Severity: Medium**

`CsvMappingTemplateController` has **no `@RequiresFeature`, no `@PreAuthorize`**, and no visible `@DirectoryId` annotation on its `directoryId` parameter. The 5 endpoints (list, create, get, update, delete) are protected only by the L1 catch-all.

This means:
- Any admin can create, modify, or delete CSV mapping templates in any directory
- Templates are a configuration artifact (column mappings for import/export) — modifying another directory's templates could corrupt bulk operations

Whether the service layer enforces directory scoping would require checking `CsvMappingTemplateService`, but the controller provides no annotation-level protection.

### W5. Playbook management has no directory-scoping or feature checks

**Severity: Medium**

`LifecyclePlaybookController` uses `@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")` on all 10 endpoints. This is equivalent to the L1 catch-all — it adds no additional restriction.

This means:
- Any admin can **create, modify, delete, preview, execute, and rollback** playbooks in **any directory**, regardless of whether they have a profile role in that directory
- Playbook *execution* does check `requireApproval` in the service layer, but playbook *management* (create/update/delete) has no such check
- An admin could create a malicious playbook in a directory they don't manage, then convince a legitimate admin to execute it

### W6. No directory-scoping on approval approve/reject operations

**Severity: Low-Medium**

`ApprovalController` passes `directoryId` as a path variable but the service methods (`approve`, `reject`, `updatePayload`) don't use it — they look up the approval by `approvalId` alone. The `directoryId` in the URL is cosmetic.

This means an admin could call `POST /api/v1/directories/{wrongDirectoryId}/approvals/{approvalId}/approve` with a mismatched directory ID and still approve the request, as long as they pass the service-layer `canViewApproval` check. The `directoryId` is never validated against the approval's actual directory.

While `canViewApproval` provides the real access control, the URL structure is misleading and the `directoryId` parameter is a phantom — it could be used for logging or audit but currently isn't.

### W7. Self-service ID is deterministic and predictable

**Severity: Low**

`AuthController.selfServiceLogin()` at line 182:
```java
UUID syntheticId = UUID.nameUUIDFromBytes(userDn.getBytes(StandardCharsets.UTF_8));
```

The self-service user's account ID is `UUID.nameUUIDFromBytes(dn)` — a deterministic UUID v3 derived from the DN. Anyone who knows a user's DN can compute their self-service account ID. This doesn't directly enable an attack (the JWT signature prevents impersonation), but if any endpoint uses the account ID as a lookup key without verifying the JWT's `sub` claim, it could be exploitable.

### W8. Superadmin bypass approval setting is a global toggle with no scoping

**Severity: Medium (design risk)**

`ApplicationSettings.superadminBypassApproval` is a single boolean that applies to ALL directories and ALL profiles. When enabled:
- Any superadmin's requests are auto-approved instantly
- No per-directory or per-profile granularity
- The setting is gated behind `@PreAuthorize("hasRole('SUPERADMIN')")`, so only superadmins can change it — but any superadmin can enable it

In an organization with multiple superadmins and strict compliance requirements, one superadmin could enable the bypass, perform operations without review, then disable it.

### W9. Approval execution catches all exceptions and keeps PENDING status

**Severity: Low**

In `ApprovalWorkflowService.approve()` at line 209-216:
```java
} catch (Exception e) {
    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
    pa.setProvisionError(msg);
    approvalRepo.save(pa);
    ...
    return toResponse(pa);
}
```

When LDAP provisioning fails, the approval stays PENDING (not marked as failed). This means:
- The same approval can be retried indefinitely
- Each retry re-executes the LDAP operation
- If the operation partially succeeded (e.g., user created but group membership failed), retries could create duplicate entries or fail with "entry already exists"

There is no idempotency guard or retry counter.

### W10. `getAuthorizedDirectoryIds` returns empty set for superadmins

**Severity: Low (subtle contract issue)**

`PermissionService.getAuthorizedDirectoryIds()` returns `Set.of()` for superadmins (meaning "unrestricted"). Callers must interpret an empty set as "all directories," not "no directories." If any caller uses `.contains(directoryId)` on the result without first checking for superadmin, it would incorrectly deny access.

The current callers handle this correctly (audit log checks for `!authorizedDirs.isEmpty()` first, and `canViewApproval` does the same), but this inverted semantic is a footgun for future callers.

---

## 4. Gaps (things that are absent)

### G1. No startup-time annotation validator

There is no mechanism to verify at application startup that all `@RequestMapping` methods under `/api/v1/directories/**` have either `@RequiresFeature` or `@PreAuthorize`. New endpoints could be deployed with only the permissive catch-all.

### G2. No request-scoped permission cache

`PermissionService.requireFeature()` issues 3+ database queries per invocation:
1. `existsByAdminAccountIdAndProfileDirectoryId` (directory access)
2. `findByAdminAccountIdAndFeatureKey` (feature override)
3. `findAllByAdminAccountId` (base role fallback)

For endpoints that make multiple permission checks per request (or when multiple aspects fire), these queries repeat. A `@RequestScope` cache would collapse them.

### G3. No feature keys for read operations

The `FeatureKey` enum has 16 keys, all covering write/management operations. There is no `USER_VIEW`, `GROUP_VIEW`, `DIRECTORY_BROWSE`, `APPROVAL_MANAGE`, or `PLAYBOOK_MANAGE` key. This makes it structurally impossible to gate read access at the feature level without adding new keys.

### G4. No per-directory PLAYBOOK_EXECUTE or PLAYBOOK_MANAGE feature key

Playbook operations are gated only by role (`ADMIN`/`SUPERADMIN`), not by feature permission. An admin whose feature overrides explicitly disable `USER_CREATE` could still execute a playbook that creates users.

### G5. No token revocation or blocklist

JWT tokens are valid until expiry. If a superadmin account is compromised, there is no way to revoke active tokens before they expire. The only mitigation is rotating the JWT signing secret (which invalidates all tokens for all users).

### G6. No `PLAYBOOK_EXECUTE` request type in approve() dispatch

`ApprovalWorkflowService.approve()` at lines 198-208 handles `USER_CREATE`, `BULK_IMPORT`, `USER_MOVE`, `GROUP_MEMBER_ADD`, and `SELF_REGISTRATION` — but **not `PLAYBOOK_EXECUTE`**. If a playbook's `requireApproval` flag routes to approval, and an approver then approves it, the approval execution block will silently do nothing (no matching `if` branch).

The `autoApprove` method at line 373-384 has the same gap — no `PLAYBOOK_EXECUTE` handler.

This means playbook approval requests can be *submitted* (the C3 fix added this) but never *executed through the approval path*. The approval will be marked APPROVED but the playbook won't actually run.

### G7. Self-rejection is not blocked

`ApprovalWorkflowService.reject()` blocks neither the original requester nor the approver from rejecting their own request. While self-*approval* is blocked (line 188), self-*rejection* is not. A requester could reject their own request to clear the queue and then resubmit with different parameters, potentially circumventing any review the approver was performing.

---

## 5. Endpoint Authorization Matrix

### Fully gated (feature + role) — 21 endpoints
User create/edit/delete/enable/disable/move/resetPassword/bulkUpdate, Group create/edit/delete/manageMembers, BulkUser import/export, BulkGroup import/export, ScheduledReport (6 endpoints), AccessReview (12 endpoints)

### Role-gated only (@PreAuthorize or class-level) — 35 endpoints
SuperadminController (6), AdminManagementController (10), DirectoryConnectionController (7), BrowseController (12), DashboardController (1), AuditDataSourceController (6), ProvisioningProfileController (16), LifecyclePlaybookController (10), ApplicationSettingsController (2), AuditLogController (1)

### Catch-all only (L1 HTTP filter, no annotation) — 24+ endpoints
SchemaController (5), DirectoryBrowseController (1), ApprovalController (6), CsvMappingTemplateController (5), UserController read endpoints (3), GroupController read endpoints (3)

### Public — 13 endpoints
AuthController login/logout/OIDC (4), SelfServiceController registration (6), settings branding (1), actuator (2)

---

## 6. Prioritized Recommendations

### Priority 1: Fix PLAYBOOK_EXECUTE approval execution gap (G6)

This is a functional bug: playbook approval requests are submitted but silently not executed on approval. Add a `PLAYBOOK_EXECUTE` branch to both `approve()` and `autoApprove()` that calls `LifecyclePlaybookService.execute()`.

### Priority 2: Add directory-scoping to read endpoints (W2)

Add `requireDirectoryAccess(principal, directoryId)` calls to:
- `UserController.search()`, `get()`, `passwordStatus()`
- `GroupController.search()`, `get()`, `getMembers()`
- `DirectoryBrowseController.browse()`
- `SchemaController` (all 5 endpoints)

This is lightweight (one line per endpoint) and prevents cross-directory data access for non-superadmins.

### Priority 3: Add directory-scoping to CsvMappingTemplate and Playbook controllers (W4, W5)

Either add `@RequiresFeature` with new feature keys, or at minimum add programmatic `requireDirectoryAccess()` calls to prevent cross-directory management.

### Priority 4: Fix countPending access control (W3)

Add directory access check to `countPending()` or filter by authorized directories.

### Priority 5: Add startup annotation validator (G1)

An `ApplicationListener<ContextRefreshedEvent>` that scans all `@RequestMapping` methods under `/api/v1/directories/**` and fails startup if any lack `@RequiresFeature` or `@PreAuthorize`. This prevents future regressions.

### Priority 6: Add idempotency guard to approval execution (W9)

Either mark failed approvals with a distinct status (e.g., `PROVISION_FAILED`) that can be retried explicitly, or add a retry counter. Prevent accidental double-execution of the underlying LDAP operation.

### Priority 7: Block self-rejection (G7)

Add `if (approver.id().equals(pa.getRequestedBy()))` check to `reject()` for consistency with `approve()`.

### Priority 8: Add request-scoped permission cache (G2)

Create a `@RequestScope` bean that loads admin's profile roles and feature overrides once per HTTP request, reuse on subsequent checks. Collapses 3N queries to 3.

### Lower priority
- W6 (phantom directoryId on approval URLs): Validate `directoryId` matches the approval's actual directory
- W7 (deterministic self-service ID): Low risk, monitor
- W8 (global bypass toggle): Consider per-directory scoping if compliance requires it
- W10 (`getAuthorizedDirectoryIds` empty-set semantic): Add Javadoc warning or return `Optional<Set<UUID>>`
- G3 (read feature keys): Depends on product decision — add `USER_VIEW` etc. if read operations should be feature-gated
- G4 (playbook feature key): Add `PLAYBOOK_MANAGE` and `PLAYBOOK_EXECUTE` feature keys
- G5 (token revocation): Standard JWT limitation; consider short TTLs + refresh tokens if this matters
