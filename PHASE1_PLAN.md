# Wave 4 (Structural) Implementation Plan

## Overview

Seven items covering annotation standardization, a startup validator, performance
optimizations, developer tooling, and DN-level enforcement (Option A).
None are currently exploitable — these are hardening and consistency improvements.

---

## 4.1 — Standardize on `@RequiresFeature` for all directory-scoped endpoints

**Goal:** Every directory-scoped endpoint gets an explicit `@RequiresFeature` (or
`@PreAuthorize` for superadmin-only), eliminating reliance on the catch-all filter.

### Step 1: Add new FeatureKey values

Add to `FeatureKey` enum (`src/main/java/com/ldapadmin/entity/enums/FeatureKey.java`):

```
PLAYBOOK_MANAGE      ("playbook.manage")       — CRUD + preview + history
PLAYBOOK_EXECUTE     ("playbook.execute")       — execute + rollback
APPROVAL_MANAGE      ("approval.manage")        — list, get, approve, reject, update payload, count
CSV_TEMPLATE_MANAGE  ("csv_template.manage")    — CRUD for CSV mapping templates
DIRECTORY_BROWSE     ("directory.browse")        — browse OU tree
SCHEMA_READ          ("schema.read")            — schema discovery (object classes, attributes)
USER_READ            ("user.read")              — search, get, password-status
GROUP_READ           ("group.read")             — search, get, list members
```

Also add all new keys to `READONLY_DEFAULT_FEATURES` in `PermissionService`:
`DIRECTORY_BROWSE`, `SCHEMA_READ`, `USER_READ`, `GROUP_READ`.

### Step 2: Add Flyway migration V37

File: `src/main/resources/db/migration/V37__wave4_feature_keys_and_index.sql`

Nothing schema-level is needed for new FeatureKey values (they are enum constants
stored via the existing `FeatureKeyConverter`). This migration will hold the 4.4
index (see below).

### Step 3: Annotate controllers

**LifecyclePlaybookController** (`controller/directory/LifecyclePlaybookController.java`):
- Replace all `@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")` with `@RequiresFeature`
- CRUD endpoints (`list`, `listEnabled`, `create`, `get`, `update`, `delete`, `listExecutions`):
  `@RequiresFeature(FeatureKey.PLAYBOOK_MANAGE)`
- `preview`: `@RequiresFeature(FeatureKey.PLAYBOOK_MANAGE)`
- `execute`: `@RequiresFeature(FeatureKey.PLAYBOOK_EXECUTE)`
- `rollback`: `@RequiresFeature(FeatureKey.PLAYBOOK_EXECUTE)`
- Add `@DirectoryId` to `directoryId` parameters and `@AuthenticationPrincipal AuthPrincipal principal` where missing (needed for aspect extraction).

**ApprovalController** (`controller/directory/ApprovalController.java`):
- All 6 endpoints: `@RequiresFeature(FeatureKey.APPROVAL_MANAGE)`
- Already has `@DirectoryId` on `directoryId` params.

**CsvMappingTemplateController** (`controller/directory/CsvMappingTemplateController.java`):
- All 5 endpoints: `@RequiresFeature(FeatureKey.CSV_TEMPLATE_MANAGE)`
- Add `@DirectoryId` annotation to `directoryId` parameters.

**DirectoryBrowseController** (`controller/directory/DirectoryBrowseController.java`):
- `browse`: `@RequiresFeature(FeatureKey.DIRECTORY_BROWSE)`
- Already has `directoryId`.

**SchemaController** (`controller/directory/SchemaController.java`):
- All 5 endpoints: `@RequiresFeature(FeatureKey.SCHEMA_READ)`
- Already has `directoryId`.

**UserController** — read endpoints only (`controller/directory/UserController.java`):
- `search` (GET /users): `@RequiresFeature(FeatureKey.USER_READ)`
- `get` (GET /users/entry): `@RequiresFeature(FeatureKey.USER_READ)`
- `passwordStatus` (GET /users/password-status): `@RequiresFeature(FeatureKey.USER_READ)`

**GroupController** — read endpoints only (`controller/directory/GroupController.java`):
- `search` (GET /groups): `@RequiresFeature(FeatureKey.GROUP_READ)`
- `get` (GET /groups/entry): `@RequiresFeature(FeatureKey.GROUP_READ)`
- `getMembers` (GET /groups/members): `@RequiresFeature(FeatureKey.GROUP_READ)`

### Step 4: Clean up ProvisioningProfileController

Leave `@PreAuthorize` as-is since superadmin-only endpoints don't need `@RequiresFeature`
(superadmins bypass all checks). The mixed ADMIN/SUPERADMIN endpoints (`list`, `get`,
`generatePassword`) already have the right `@PreAuthorize`.

---

## 4.2 — Startup-time annotation validator

**Goal:** Fail application startup if any `@RequestMapping` method inside a
`/api/v1/directories/{directoryId}/` controller lacks an explicit authorization
annotation.

### Step 1: Create `AuthorizationAnnotationValidator`

File: `src/main/java/com/ldapadmin/auth/AuthorizationAnnotationValidator.java`

Implements `ApplicationListener<ContextRefreshedEvent>` (or `SmartInitializingSingleton`).

Logic:
1. Scan all beans annotated with `@RestController`.
2. For each handler method with a request mapping containing `/directories/{directoryId}`:
   - Assert it has `@RequiresFeature` OR `@PreAuthorize` OR is in a class with
     class-level `@PreAuthorize`.
3. If any method fails validation, throw `IllegalStateException` with the list of
   unannotated methods.

Exclude self-service and public endpoints via a configurable allowlist of path
prefixes (e.g. `/api/v1/self-service/`, `/api/v1/auth/`).

### Step 2: Add tests

Write a unit test that constructs the validator with a mock ApplicationContext
containing a deliberately-unannotated controller and verifies it throws.

---

## 4.3 — Request-scoped permission cache

**Goal:** Cache `AdminProfileRole` and `AdminFeaturePermission` lookups per HTTP
request to avoid repeated DB hits (currently up to 5 queries per request).

### Step 1: Create `RequestScopedPermissionCache`

File: `src/main/java/com/ldapadmin/auth/RequestScopedPermissionCache.java`

```java
@Component
@RequestScope
public class RequestScopedPermissionCache {
    private List<AdminProfileRole> roles;           // null = not loaded
    private Map<FeatureKey, Optional<AdminFeaturePermission>> featureOverrides = new HashMap<>();

    public List<AdminProfileRole> getRoles(UUID adminId, AdminProfileRoleRepository repo) {
        if (roles == null) {
            roles = repo.findAllByAdminAccountId(adminId);
        }
        return roles;
    }

    public Optional<AdminFeaturePermission> getFeatureOverride(
            UUID adminId, FeatureKey key, AdminFeaturePermissionRepository repo) {
        return featureOverrides.computeIfAbsent(key,
                k -> repo.findByAdminAccountIdAndFeatureKey(adminId, k));
    }
}
```

### Step 2: Inject into `PermissionService`

Replace direct repository calls with cache-mediated calls:
- `requireDirectoryAccess()` → use cached roles list, check if any role's profile
  has the target directoryId
- `requireFeature()` → use cached feature override + cached roles for base-role check
- `getAuthorizedDirectoryIds()` → derive from cached roles

This collapses all queries into at most 2 per request: one for roles (eagerly fetched
with joins), one for the specific feature override.

### Step 3: Add `@EntityGraph` or fetch join to avoid N+1

Add a custom query to `AdminProfileRoleRepository`:
```java
@EntityGraph(attributePaths = {"profile", "profile.directory"})
List<AdminProfileRole> findAllWithProfileAndDirectoryByAdminAccountId(UUID adminAccountId);
```

This eliminates the N+1 lazy-load problem in `getAuthorizedDirectoryIds()`.

---

## 4.4 — Missing DB index on `admin_profile_roles(admin_account_id)`

**Goal:** Add a dedicated index for the most-queried column.

### Step 1: Add to V37 migration

```sql
CREATE INDEX IF NOT EXISTS idx_apr_admin_account
    ON admin_profile_roles (admin_account_id);
```

The existing UNIQUE constraint on `(admin_account_id, profile_id)` helps paired
lookups but not `findAllByAdminAccountId` which queries on `admin_account_id` alone.

---

## 4.5 — Replace over-fetched `findAllByAdminAccountId()` with targeted queries

**Goal:** Eliminate loading full entity graphs when only existence checks are needed.

### Step 1: Add repository methods

In `AdminProfileRoleRepository`:

```java
boolean existsByAdminAccountIdAndBaseRole(UUID adminAccountId, BaseRole baseRole);

@Query("SELECT DISTINCT r.profile.directory.id FROM AdminProfileRole r WHERE r.adminAccount.id = :adminId")
Set<UUID> findDistinctDirectoryIdsByAdminAccountId(@Param("adminId") UUID adminAccountId);
```

### Step 2: Update PermissionService

- `requireFeature()` line 115: Replace `findAllByAdminAccountId().stream().anyMatch(...)`
  with `existsByAdminAccountIdAndBaseRole(principal.id(), BaseRole.ADMIN)`.
- `getAuthorizedDirectoryIds()` line 133: Replace `findAllByAdminAccountId().stream().map(...)`
  with `findDistinctDirectoryIdsByAdminAccountId(principal.id())`.

**Note:** If 4.3 (request-scoped cache) is implemented first, 4.5 becomes less
critical since the roles are fetched once per request anyway. Implement 4.5 only
for the `getAuthorizedDirectoryIds` JPQL query (avoids loading entities just to
extract IDs), and skip the `existsBy` change in favor of the cache.

---

## 4.6 — Build-time feature permission matrix

**Goal:** Auto-generate a reference document mapping every endpoint to its
authorization requirements.

### Step 1: Create annotation processor or test

A test-based approach is simpler and more maintainable:

File: `src/test/java/com/ldapadmin/auth/FeaturePermissionMatrixTest.java`

Logic:
1. Use Spring's `RequestMappingHandlerMapping` to enumerate all endpoints.
2. For each endpoint, check for `@RequiresFeature`, `@PreAuthorize`, or class-level
   annotations.
3. Generate a markdown table:
   `| HTTP Method | Path | Auth Mechanism | Feature Key / Role |`
4. Write to `target/feature-permission-matrix.md`.
5. Optionally assert against a checked-in snapshot to detect drift.

This runs as part of `mvn test` and produces the matrix automatically.

---

## 4.7 — DN-level enforcement (Option A)

**Goal:** Restrict admin operations to DNs within their authorized profile OUs.
An admin with access to `ou=engineering,dc=example,dc=com` cannot read/write
entries in `ou=sales,dc=example,dc=com`.

### Phase 1: Core infrastructure

#### Step 1: Add `getAuthorizedOuDns()` to PermissionService

```java
public Set<String> getAuthorizedOuDns(AuthPrincipal principal, UUID directoryId) {
    if (principal.isSuperadmin()) return Set.of(); // empty = unrestricted
    return profileRoleRepo.findAllByAdminAccountIdAndProfileDirectoryId(principal.id(), directoryId)
            .stream()
            .map(r -> r.getProfile().getTargetOuDn())
            .collect(Collectors.toSet());
}
```

#### Step 2: Add `requireDnWithinScope()` to PermissionService

```java
public void requireDnWithinScope(AuthPrincipal principal, UUID directoryId, String dn) {
    if (principal.isSuperadmin()) return;
    Set<String> allowedOus = getAuthorizedOuDns(principal, directoryId);
    if (allowedOus.isEmpty()) {
        throw new AccessDeniedException("No profile access in directory [" + directoryId + "]");
    }
    String normalizedDn = dn.toLowerCase(Locale.ROOT);
    boolean inScope = allowedOus.stream()
            .anyMatch(ou -> normalizedDn.endsWith("," + ou.toLowerCase(Locale.ROOT))
                         || normalizedDn.equalsIgnoreCase(ou));
    if (!inScope) {
        throw new AccessDeniedException("DN [" + dn + "] is outside authorized OUs");
    }
}
```

**DN matching rule:** A DN is in-scope if it is a descendant of (ends with `,<ou>`)
or equal to any authorized OU DN.

#### Step 3: Add `scopeBaseDn()` helper for search operations

For search/browse operations where we need to filter results rather than reject
outright:

```java
public String scopeBaseDn(AuthPrincipal principal, UUID directoryId, String requestedBaseDn) {
    if (principal.isSuperadmin()) return requestedBaseDn;
    Set<String> allowedOus = getAuthorizedOuDns(principal, directoryId);
    // If requestedBaseDn is within an allowed OU, allow it
    // If requestedBaseDn is a parent of allowed OUs, narrow to the most specific common ancestor
    // If no overlap, throw AccessDeniedException
    // ... (implementation detail)
}
```

**Note:** For the initial implementation, the simpler approach is to validate that
`requestedBaseDn` (or the directory baseDn if none specified) is within or equal to
an authorized OU. If the requested baseDn is broader than the allowed OUs, reject.

#### Step 4: Add repository method

In `AdminProfileRoleRepository`:
```java
List<AdminProfileRole> findAllByAdminAccountIdAndProfileDirectoryId(UUID adminId, UUID directoryId);
```
(Already exists at line 22 as `findAllByProfileDirectoryId` — need the admin-scoped version.)

### Phase 2: Enforce on write paths

#### Step 5: Add DN validation to write operations in LdapOperationService

Modify these methods to call `requireDnWithinScope()`:
- `createEntry()` — validate the target DN
- `updateEntry()` — validate the entry DN
- `deleteEntry()` — validate the entry DN
- `enableUser()` / `disableUser()` — validate the user DN
- `resetPassword()` — validate the user DN
- `moveEntry()` — validate both source and destination DNs
- `addGroupMember()` / `removeGroupMember()` — validate the group DN
- `bulkImportUsers()` — validate each entry DN in the import batch
- `bulkUpdateAttributes()` — validate each target DN

#### Step 6: Add DN validation to playbook execution

In `LifecyclePlaybookService.executeImmediate()`:
- After `requirePlaybook()`, call `permissionService.requireDnWithinScope(principal, directoryId, targetDn)`

### Phase 3: Enforce on read paths

#### Step 7: Add DN validation to read operations

In `LdapOperationService`:
- `searchUsers()` — validate `baseDn` parameter against authorized OUs
- `getUser()` — validate the requested DN
- `searchGroups()` — validate `baseDn` parameter
- `getGroup()` — validate the requested DN
- `getGroupMembers()` — validate the group DN
- `browse()` — validate the `dn` parameter

In `BulkUserController` / `BulkGroupController`:
- `exportUsers()` — validate `baseDn` parameter
- `exportGroups()` — validate `baseDn` parameter

#### Step 8: Handle null/default baseDn

When `baseDn` is null (meaning "search from directory root"), resolve it to the
admin's authorized OUs:
- If admin has exactly one OU: use it as the baseDn
- If admin has multiple OUs: perform multiple searches (one per OU) and merge
  results, OR reject with a 400 asking the caller to specify a baseDn

The simpler initial approach: require non-superadmin callers to specify a `baseDn`
that falls within their authorized OUs. If omitted, default to the first (or only)
authorized OU.

---

## Implementation Order

| Priority | Item | Depends on | Estimated scope |
|----------|------|------------|-----------------|
| 1        | **4.4** — DB index | None | 1 migration file |
| 2        | **4.1** — Standardize annotations | None | 8 new FeatureKey values, 7 controllers, ~30 annotations |
| 3        | **4.2** — Startup validator | 4.1 (validates the annotations exist) | 1 new class + 1 test |
| 4        | **4.7 Phase 1** — Core infrastructure | None | 3 new methods in PermissionService, 1 repo method |
| 5        | **4.7 Phase 2** — Write path enforcement | 4.7 Phase 1 | ~10 call sites in LdapOperationService + LifecyclePlaybookService |
| 6        | **4.7 Phase 3** — Read path enforcement | 4.7 Phase 1 | ~8 call sites in LdapOperationService + bulk controllers |
| 7        | **4.5** — Targeted queries | None (but 4.3 affects priority) | 2 repo methods, 2 PermissionService edits |
| 8        | **4.3** — Request-scoped cache | None | 1 new class, PermissionService refactor, 1 EntityGraph query |
| 9        | **4.6** — Permission matrix test | 4.1 (more useful after standardization) | 1 test class |

Items 1-3 can be committed as one wave. Items 4-6 (4.7) as a second wave. Items 7-9 as a third.

---

## Files to create

| File | Purpose |
|------|---------|
| `src/main/resources/db/migration/V37__wave4_feature_keys_and_index.sql` | Index on admin_profile_roles(admin_account_id) |
| `src/main/java/com/ldapadmin/auth/AuthorizationAnnotationValidator.java` | Startup validator |
| `src/main/java/com/ldapadmin/auth/RequestScopedPermissionCache.java` | Per-request cache |
| `src/test/java/com/ldapadmin/auth/FeaturePermissionMatrixTest.java` | Auto-generated permission matrix |

## Files to modify

| File | Changes |
|------|---------|
| `FeatureKey.java` | +8 enum values |
| `PermissionService.java` | +3 methods (getAuthorizedOuDns, requireDnWithinScope, scopeBaseDn), update READONLY_DEFAULT_FEATURES, inject cache |
| `AdminProfileRoleRepository.java` | +3 query methods |
| `LifecyclePlaybookController.java` | Replace @PreAuthorize with @RequiresFeature, add @DirectoryId + @AuthenticationPrincipal |
| `ApprovalController.java` | +@RequiresFeature on all 6 endpoints |
| `CsvMappingTemplateController.java` | +@RequiresFeature + @DirectoryId on all 5 endpoints |
| `DirectoryBrowseController.java` | +@RequiresFeature on browse |
| `SchemaController.java` | +@RequiresFeature on all 5 endpoints |
| `UserController.java` | +@RequiresFeature on 3 read endpoints |
| `GroupController.java` | +@RequiresFeature on 3 read endpoints |
| `LdapOperationService.java` | +requireDnWithinScope calls on ~18 methods |
| `LifecyclePlaybookService.java` | +requireDnWithinScope in executeImmediate() |
| `BulkUserController.java` | baseDn scope validation in export |
| `BulkGroupController.java` | baseDn scope validation in export |
