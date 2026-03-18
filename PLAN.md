# LDAPAdmin Feature Implementation Plan

Ordered for maximum implementation efficiency — each feature builds on patterns and code from the previous one, minimizing context-switching and maximizing code reuse.

---

## Phase 1: Delete Entry (Directory Browser)

**Why first:** Already stubbed (disabled button exists). Smallest feature, establishes the delete pattern reused by later features. Warms up the BrowseController/BrowseService extension pattern.

### Backend

1. **Add `ENTRY_DELETE` to `AuditAction` enum**
   - File: `src/main/java/com/ldapadmin/entity/enums/AuditAction.java`
   - Add `ENTRY_DELETE("entry.delete")` after `ENTRY_UPDATE`

2. **Add `deleteEntry()` to `LdapBrowseService`**
   - File: `src/main/java/com/ldapadmin/ldap/LdapBrowseService.java`
   - Simple delete: `conn.delete(dn)` with `checkResult()` pattern (mirrors `LdapUserService.deleteUser`)
   - Add recursive variant: search one-level children first, delete bottom-up (OpenLDAP rejects delete on non-leaf entries)
   - Method signature: `deleteEntry(DirectoryConnection dc, String dn, boolean recursive)`

3. **Add `@DeleteMapping` to `BrowseController`**
   - File: `src/main/java/com/ldapadmin/controller/superadmin/BrowseController.java`
   - Params: `@PathVariable UUID directoryId`, `@RequestParam String dn`, `@RequestParam(defaultValue="false") boolean recursive`
   - Compute parent DN from the deleted entry's DN
   - Call `browseService.deleteEntry(dc, dn, recursive)`
   - Audit with `ENTRY_DELETE`
   - Return parent's `BrowseResult` (same pattern as `createEntry`) for tree refresh

### Frontend

4. **Add `deleteEntry()` to `browse.js`**
   - File: `frontend/src/api/browse.js`
   - `export const deleteEntry = (dirId, dn, recursive = false) => client.delete(base(dirId), { params: { dn, recursive } })`

5. **Enable Delete button and add confirmation dialog in `DirectoryBrowserView.vue`**
   - File: `frontend/src/views/superadmin/DirectoryBrowserView.vue`
   - Replace the disabled "Delete Entry" button with an enabled button that sets `deletingEntry = true`
   - Show a `ConfirmDialog` (already exists as a component) asking "Delete {dn}?"
   - Include a checkbox: "Delete recursively (include all children)" — default unchecked
   - On confirm: call `deleteEntry(selectedDirId, selectedDn, recursive)`
   - On success: refresh tree via `treeRef.value.refreshNode(parentDn, browseResult.children)`, clear selection, show success notification
   - On error: show error notification

---

## Phase 2: Entry Move / Rename (ModDN)

**Why second:** Reuses the BrowseController extension pattern just established. ModDN is a single LDAP operation. The UI builds on the Actions menu pattern.

### Backend

1. **Add `ENTRY_MOVE` and `ENTRY_RENAME` to `AuditAction`**
   - File: `src/main/java/com/ldapadmin/entity/enums/AuditAction.java`

2. **Add `moveEntry()` and `renameEntry()` to `LdapBrowseService`**
   - File: `src/main/java/com/ldapadmin/ldap/LdapBrowseService.java`
   - `moveEntry(dc, dn, newParentDn)` — calls `conn.modifyDN(dn, currentRdn, true, newParentDn)` (mirrors `LdapUserService.moveUser`)
   - `renameEntry(dc, dn, newRdn)` — calls `conn.modifyDN(dn, newRdn, true)` (rename in place)

3. **Add DTO: `MoveEntryRequest`**
   - File: `src/main/java/com/ldapadmin/dto/ldap/MoveEntryRequest.java`
   - Record with `@NotBlank String newParentDn`

4. **Add DTO: `RenameEntryRequest`**
   - File: `src/main/java/com/ldapadmin/dto/ldap/RenameEntryRequest.java`
   - Record with `@NotBlank String newRdn`

5. **Add endpoints to `BrowseController`**
   - `POST .../browse/move?dn=` with `MoveEntryRequest` body → audit as `ENTRY_MOVE`, return new parent's browse result
   - `POST .../browse/rename?dn=` with `RenameEntryRequest` body → audit as `ENTRY_RENAME`, return parent's browse result

### Frontend

6. **Add API functions to `browse.js`**
   - `moveEntry(dirId, dn, newParentDn)`
   - `renameEntry(dirId, dn, newRdn)`

7. **Add Move/Rename options to Actions dropdown**
   - File: `frontend/src/views/superadmin/DirectoryBrowserView.vue`
   - "Move Entry" → opens a modal with a DN input field (target parent DN), pre-populated with current parent
   - "Rename Entry" → opens a modal with a text input for new RDN, pre-populated with current RDN
   - On success: refresh tree node, re-select the entry at its new DN

---

## Phase 3: Password Management

**Why third:** High-value feature for daily admin use. Backend touches UserController (already well-established), frontend extends the user detail view.

### Backend

1. **Add `resetPassword()` to `LdapUserService`**
   - File: `src/main/java/com/ldapadmin/ldap/LdapUserService.java`
   - Uses `Modification(REPLACE, "userPassword", newPassword)`
   - Accept optional hash algorithm param (SSHA, SHA, cleartext) — OpenLDAP typically stores hashed

2. **Add `USER_RESET_PASSWORD` to `FeatureKey` enum**
   - File: `src/main/java/com/ldapadmin/entity/enums/FeatureKey.java`

3. **Add `PASSWORD_RESET` to `AuditAction`**
   - File: `src/main/java/com/ldapadmin/entity/enums/AuditAction.java`

4. **Add DTO: `ResetPasswordLdapRequest`**
   - File: `src/main/java/com/ldapadmin/dto/ldap/ResetPasswordLdapRequest.java`
   - Record: `@NotBlank String newPassword`

5. **Add endpoint to `UserController`**
   - `POST /api/v1/directories/{directoryId}/users/reset-password?dn=`
   - `@RequiresFeature(FeatureKey.USER_RESET_PASSWORD)`
   - Calls `operationService.resetPassword(directoryId, principal, dn, req.newPassword())`
   - Audits as `PASSWORD_RESET`

6. **Add `resetPassword()` to `LdapOperationService`**
   - Standard pattern: load directory, call service, audit

### Frontend

7. **Add `resetPassword()` to user API**
   - File: `frontend/src/api/users.js`

8. **Add password reset UI to `UserListView.vue`**
   - File: `frontend/src/views/users/UserListView.vue`
   - Add a "Reset Password" button in the user detail panel / user actions
   - Modal with password input + confirm password input
   - Strength indicator (optional — basic length/complexity check)
   - On success: show notification, close modal

---

## Phase 4: Global LDAP Search

**Why fourth:** Reuses browse API patterns, but adds a new view. Valuable power-user tool that complements the tree browser.

### Backend

1. **Add `searchEntries()` to `LdapBrowseService`**
   - File: `src/main/java/com/ldapadmin/ldap/LdapBrowseService.java`
   - Params: `DirectoryConnection dc, String baseDn, SearchScope scope, String filter, List<String> attributes, int sizeLimit`
   - Returns `List<LdapEntryResponse>` (reuse existing DTO)
   - Uses `SearchRequest` with configurable scope (BASE, ONE, SUB)
   - Size limit default: 100 (prevent runaway queries)

2. **Add search endpoint to `BrowseController`**
   - `GET .../browse/search?baseDn=&scope=&filter=&attributes=&limit=`
   - Returns `List<LdapEntryResponse>`

### Frontend

3. **Add `searchEntries()` to `browse.js`**

4. **Create `LdapSearchView.vue`**
   - File: `frontend/src/views/superadmin/LdapSearchView.vue`
   - Form fields: Directory picker, Base DN input, Scope dropdown (Base/One-level/Subtree), Filter input, Attributes input (comma-separated, optional), Size limit
   - Results displayed in a DataTable (reuse existing component)
   - Click a result DN → navigate to Directory Browser with that DN selected (or show inline detail)
   - Save search history in localStorage (last 10 searches)

5. **Add route and navigation**
   - Route: `/superadmin/search`
   - Add "LDAP Search" link to superadmin navigation in `AppLayout.vue`

---

## Phase 5: LDIF Export

**Why fifth:** Export is read-only and simpler than import. Builds on the browse/search infrastructure from Phase 4. Establishes the LDIF format handling reused by Phase 6.

### Backend

1. **Create `LdifService`**
   - File: `src/main/java/com/ldapadmin/ldap/LdifService.java`
   - `exportEntry(DirectoryConnection dc, String dn)` → single entry as LDIF string
   - `exportSubtree(DirectoryConnection dc, String baseDn, SearchScope scope)` → full subtree as LDIF
   - Use UnboundID's `LDIFWriter` / manual formatting (dn:, attributes, base64 for binary)
   - Stream results to `OutputStream` for large exports (same pattern as `BulkUserService.exportUsers`)

2. **Add export endpoints to `BrowseController`**
   - `GET .../browse/export/ldif?dn=&scope=` → returns `application/ldif` content type
   - Scope: `base` (single entry), `one` (one-level), `sub` (full subtree)
   - Response as file download with `Content-Disposition: attachment; filename="export.ldif"`

### Frontend

3. **Add `exportLdif()` to `browse.js`**
   - Returns blob, triggers browser download

4. **Add "Export LDIF" to Actions dropdown in `DirectoryBrowserView.vue`**
   - Sub-options: "This entry only", "This entry + direct children", "Entire subtree"
   - Triggers download directly

5. **Also add export button to `LdapSearchView.vue`**
   - "Export results as LDIF" button after running a search

---

## Phase 6: LDIF Import

**Why sixth:** Builds directly on Phase 5's LDIF parsing knowledge. Import is the write counterpart.

### Backend

1. **Add import method to `LdifService`**
   - `importLdif(DirectoryConnection dc, InputStream ldifContent, ConflictHandling conflict)` → `LdifImportResult`
   - Parse LDIF using UnboundID's `LDIFReader`
   - Process each entry: attempt `conn.add()`, handle conflicts (SKIP / UPDATE / FAIL — reuse existing `ConflictHandling` enum)
   - Support change records (add/modify/delete/moddn) in addition to content records
   - Return: `LdifImportResult(int added, int updated, int skipped, int failed, List<LdifImportError> errors)`

2. **Add DTO: `LdifImportResult`**
   - File: `src/main/java/com/ldapadmin/dto/ldap/LdifImportResult.java`

3. **Add import endpoint to `BrowseController`**
   - `POST .../browse/import/ldif` with multipart file upload
   - Params: `@RequestParam ConflictHandling conflictHandling` (default SKIP)
   - Returns `LdifImportResult`
   - Audit each entry creation/modification individually

### Frontend

4. **Add `importLdif()` to `browse.js`**
   - Multipart form data upload (same pattern as `BulkUserService` CSV import)

5. **Create `LdifImportModal.vue`**
   - File: `frontend/src/components/LdifImportModal.vue`
   - File picker (drag-and-drop + click), accepts `.ldif` files
   - Conflict handling dropdown: Skip / Update / Fail
   - Dry-run toggle (optional — adds `?dryRun=true` param)
   - Progress display: shows results summary after completion (added/updated/skipped/failed)
   - Error list: expandable panel showing each failed entry with error message

6. **Add "Import LDIF" to Actions dropdown in `DirectoryBrowserView.vue`**
   - Opens the `LdifImportModal`
   - On success: refresh tree from root

---

## Phase 7: Connection Health Dashboard

**Why seventh:** Read-only, no LDAP writes. Provides operational visibility. Good to build after all the write features are done.

### Backend

1. **Add `getServerInfo()` to `LdapBrowseService`**
   - Read Root DSE (`""` base, scope BASE): extract `namingContexts`, `supportedLDAPVersion`, `vendorName`, `vendorVersion`, `supportedControl`, `supportedExtension`
   - Return as `DirectoryServerInfo` DTO

2. **Add pool stats method to `LdapConnectionFactory`**
   - File: `src/main/java/com/ldapadmin/ldap/LdapConnectionFactory.java`
   - Expose `getPoolStatistics(UUID directoryId)` → `ConnectionPoolStats(int active, int idle, int maxSize, long totalCreated, long totalClosed, long failedConnects)`
   - UnboundID's `LDAPConnectionPool` exposes `getConnectionPoolStatistics()`

3. **Create DTO: `DirectoryHealthResponse`**
   - Fields: `DirectoryServerInfo serverInfo`, `ConnectionPoolStats poolStats`, `boolean reachable`, `long latencyMs`, `Instant lastChecked`

4. **Add endpoint to `DirectoryConnectionController` (superadmin)**
   - `GET /api/v1/superadmin/directories/{id}/health`
   - Perform a lightweight bind/search to measure latency
   - Return `DirectoryHealthResponse`

### Frontend

5. **Create `DirectoryHealthView.vue`**
   - File: `frontend/src/views/superadmin/DirectoryHealthView.vue`
   - Card per directory showing: connection status (green/red dot), latency, pool stats, server version
   - Auto-refresh toggle (poll every 30s)
   - "Evict Pool" button (already exists as endpoint)

6. **Add route and navigation**
   - Route: `/superadmin/health`
   - Add "Connection Health" to superadmin nav in `AppLayout.vue`

---

## Phase 8: Referential Integrity Checker

**Why eighth:** Read-only analysis tool. Reuses search infrastructure from Phase 4.

### Backend

1. **Create `IntegrityCheckService`**
   - File: `src/main/java/com/ldapadmin/ldap/IntegrityCheckService.java`
   - `checkBrokenMembers(DirectoryConnection dc, String baseDn)` → search all entries with `member` or `uniqueMember` attributes, verify each referenced DN exists
   - `checkOrphanedEntries(DirectoryConnection dc, String baseDn)` → find entries whose parent DN doesn't exist
   - `checkEmptyGroups(DirectoryConnection dc, String baseDn)` → groups with no members
   - Returns `IntegrityReport(List<IntegrityIssue> issues)` with issue type, DN, description

2. **Add DTO: `IntegrityReport`, `IntegrityIssue`**

3. **Add endpoint to `BrowseController`**
   - `POST .../browse/integrity-check?baseDn=&checks=` (POST because it's a long-running operation)
   - `checks` param: comma-separated list of check types to run
   - Returns `IntegrityReport`

### Frontend

4. **Create `IntegrityCheckView.vue`**
   - File: `frontend/src/views/superadmin/IntegrityCheckView.vue`
   - Directory picker, base DN input, checkboxes for which checks to run
   - "Run Check" button with loading spinner
   - Results as a grouped table: issue type → list of affected DNs with descriptions
   - Click a DN → navigate to browser

5. **Add route and navigation**
   - Route: `/superadmin/integrity`
   - Add to superadmin nav

---

## Phase 9: Audit Log Export

**Why ninth:** Quick win. The audit query infrastructure already exists. Just add a download endpoint.

### Backend

1. **Add export endpoint to `AuditLogController`**
   - `GET /api/v1/audit/export?format=csv&directoryId=&action=&from=&to=`
   - Reuse existing `AuditQueryService` filtering
   - Stream results to CSV (same pattern as `BulkUserService.exportUsers`)
   - `Content-Disposition: attachment; filename="audit-log-{date}.csv"`

### Frontend

2. **Add "Export" button to `AuditLogView.vue`**
   - File: `frontend/src/views/audit/AuditLogView.vue`
   - Button next to the filter controls
   - Exports with current filter applied
   - Downloads as CSV

---

## Phase 10: Quick UI Wins

**Why last:** Polish items that don't add new backend capabilities but improve UX. Can be done in a single pass through the frontend.

### 10a. DN Copy-to-Clipboard
- Add a copy icon button next to every DN display
- Files: `DirectoryBrowserView.vue`, `EditEntryForm.vue`, `UserListView.vue`, `GroupListView.vue`
- Use `navigator.clipboard.writeText(dn)`

### 10b. Entry Count Badges on Tree Nodes
- File: `DnTree.vue`
- Show child count in a small gray badge next to expandable nodes
- Data already available from `children.length` in browse results

### 10c. Bulk Group Membership
- File: `frontend/src/views/groups/GroupListView.vue`
- Add "Add Multiple Members" button that accepts a textarea of DNs (one per line)
- Backend: Add `POST /api/v1/directories/{directoryId}/groups/members/bulk?dn=` to `GroupController`
- Calls `addMember()` in a loop, returns summary (added/failed)

### 10d. Saved Searches
- Extend `LdapSearchView.vue` from Phase 4
- Save searches to localStorage with a name
- Dropdown to load saved searches
- No backend changes needed

---

## Summary: Execution Order & Estimated Scope

| Phase | Feature | New Files | Modified Files | Backend | Frontend |
|-------|---------|-----------|----------------|---------|----------|
| 1 | Delete Entry | 0 | 5 | Small | Small |
| 2 | Move/Rename Entry | 2 DTOs | 4 | Small | Medium |
| 3 | Password Management | 1 DTO | 6 | Medium | Medium |
| 4 | Global LDAP Search | 1 view | 4 | Medium | Medium |
| 5 | LDIF Export | 1 service | 4 | Medium | Small |
| 6 | LDIF Import | 1 DTO, 1 component | 3 | Large | Medium |
| 7 | Health Dashboard | 2 DTOs, 1 view | 3 | Medium | Medium |
| 8 | Integrity Checker | 1 service, 2 DTOs, 1 view | 2 | Large | Medium |
| 9 | Audit Export | 0 | 2 | Small | Small |
| 10 | UI Quick Wins | 0 | ~6 | Small | Small |

**Total: ~10 new files, ~25 modified files across all phases.**

Each phase is independently deployable and testable. Phases 1-3 are the highest-value features for daily OpenLDAP administration.
