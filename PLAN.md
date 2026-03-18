# Plan: Add Entry Creation to the Directory Browser

## Overview

Add the ability to create **any** LDAP entry (leaf or non-leaf) from the superadmin Directory Browser. The create form is schema-driven: the user picks objectClasses, and the form dynamically renders required/optional attribute fields based on the directory's live schema.

---

## Backend Changes

### 1. New `ENTRY_CREATE` audit action

**File:** `src/main/java/com/ldapadmin/entity/enums/AuditAction.java`

Add a new enum value:
```java
ENTRY_CREATE("entry.create"),
```

### 2. Add `createEntry` to `LdapBrowseService`

**File:** `src/main/java/com/ldapadmin/ldap/LdapBrowseService.java`

New method that performs a generic `conn.add()`:
```java
public void createEntry(DirectoryConnection dc, String dn,
                        Map<String, List<String>> attributes) {
    List<Attribute> ldapAttrs = new ArrayList<>();
    attributes.forEach((name, values) ->
        ldapAttrs.add(new Attribute(name, values.toArray(new String[0]))));

    connectionFactory.withConnection(dc, conn -> {
        LDAPResult result = conn.add(new AddRequest(dn, ldapAttrs));
        if (result.getResultCode() != ResultCode.SUCCESS) {
            throw new LdapOperationException(
                "createEntry failed for [" + dn + "]: "
                + result.getResultCode() + " — " + result.getDiagnosticMessage());
        }
        log.info("Created LDAP entry {}", dn);
        return null;
    });
}
```

This is intentionally separate from `LdapUserService.createUser` because it lives in the browse/superadmin domain and doesn't carry user/group semantics.

### 3. Add `POST` endpoint + schema endpoints to `BrowseController`

**File:** `src/main/java/com/ldapadmin/controller/superadmin/BrowseController.java`

Add two new endpoints:

**a) `POST /api/v1/superadmin/directories/{directoryId}/browse`** — creates an entry:
- Accepts `@Valid @RequestBody CreateEntryRequest req`
- Calls `browseService.createEntry(dc, req.dn(), req.attributes())`
- Records audit via `AuditService` with `AuditAction.ENTRY_CREATE`
- Returns `BrowseResult` of the **parent DN** (so the frontend can refresh the tree under the parent)

**b) Schema proxy endpoints** — the existing schema endpoints require an `AuthPrincipal` (realm-scoped), but the browser is superadmin-only and doesn't go through realm auth. Add thin schema endpoints on the browse controller:

```
GET /api/v1/superadmin/directories/{directoryId}/browse/schema/object-classes
GET /api/v1/superadmin/directories/{directoryId}/browse/schema/object-classes/bulk?names=...
```

These call `LdapSchemaService` directly (not through `LdapOperationService`) since superadmin bypasses realm/feature checks.

### 4. Inject dependencies into `BrowseController`

Add `LdapSchemaService`, `AuditService`, and import `CreateEntryRequest` to the controller.

---

## Frontend Changes

### 5. Add API functions for create + schema

**File:** `frontend/src/api/browse.js`

```javascript
export const createEntry = (dirId, data) =>
  client.post(`/superadmin/directories/${dirId}/browse`, data)

export const browseObjectClasses = (dirId) =>
  client.get(`/superadmin/directories/${dirId}/browse/schema/object-classes`)

export const browseObjectClassesBulk = (dirId, names) =>
  client.get(`/superadmin/directories/${dirId}/browse/schema/object-classes/bulk`, {
    params: { names: names.join(',') },
  })
```

### 6. New `CreateEntryForm.vue` component

**File:** `frontend/src/components/CreateEntryForm.vue` (new file)

A self-contained form component that handles entry creation. Props: `directoryId`, `parentDn`. Emits: `created`, `cancel`.

**Form fields (rendered in order):**

| Field | Type | Notes |
|-------|------|-------|
| objectClass | Multi-select dropdown | Populated from `browseObjectClasses`. Common types (`organizationalUnit`, `inetOrgPerson`, `groupOfUniqueNames`, `container`, `top`) pinned to top as suggestions. |
| RDN attribute | Dropdown | Auto-populated from the required attributes of the selected objectClasses. User picks which attribute forms the RDN. |
| RDN value | Text input | The value for the RDN attribute. |
| Computed DN | Read-only display | Shows `{rdnAttr}={rdnValue},{parentDn}` — updates live as the user types. |
| Required attributes | Text inputs | One field per required attribute (from bulk schema lookup), pre-filled: RDN attr is locked to RDN value, `objectClass` is hidden (auto-set). |
| Optional attributes | Collapsible section | Rendered as add-able rows. User clicks "+ Add attribute" to pick from the optional list and provide a value. Multi-valued attributes get an "add value" button. |

**Behavior:**
1. On mount, fetch objectClass list via `browseObjectClasses(dirId)`.
2. When objectClasses change, fetch merged attrs via `browseObjectClassesBulk(dirId, selectedClasses)`.
3. On submit, assemble `{ dn, attributes }` payload and call `createEntry(dirId, payload)`.
4. On success, emit `created` with the parent DN so the tree can refresh.
5. On error, display the server error message inline.

**Smart defaults / pre-population logic:**
- If user selects `organizationalUnit` → auto-set RDN attr to `ou`
- If user selects `inetOrgPerson` → auto-set RDN attr to `uid`
- If user selects `groupOfUniqueNames` or `groupOfNames` → auto-set RDN attr to `cn`
- If user selects `container` → auto-set RDN attr to `cn`
- The `objectClass` attribute is always included automatically (never shown as a field)
- The `top` objectClass is auto-included if not already selected

### 7. Integrate into `DirectoryBrowserView.vue`

**File:** `frontend/src/views/superadmin/DirectoryBrowserView.vue`

**Changes:**

a) Add a `[+ New Entry]` button in the right panel header, visible when a DN is selected and not in create mode.

b) Add a `creatingEntry` ref. When true, the right panel replaces the attribute table with `<CreateEntryForm>`, passing `selectedDirId` and `selectedDn` as the parent.

c) On `@created` event from the form:
   - Set `creatingEntry = false`
   - Re-browse the parent DN to refresh the tree
   - Invalidate `DnTree`'s children cache for the parent so the new node appears

d) On `@cancel`, set `creatingEntry = false` and return to attribute view.

### 8. Add tree refresh capability to `DnTree.vue`

**File:** `frontend/src/components/DnTree.vue`

Expose a method (via `defineExpose`) or accept a `refreshKey` prop so the parent can trigger a re-fetch of a specific node's children. When the parent DN's children are invalidated:
- Clear the `childrenMap` entry for that DN
- Re-invoke `loadChildren` if the node is currently expanded
- Mark the node as `hasChildren: true` (it now has at least one child)

---

## File Change Summary

| File | Action |
|------|--------|
| `AuditAction.java` | Add `ENTRY_CREATE` enum value |
| `LdapBrowseService.java` | Add `createEntry()` method |
| `BrowseController.java` | Add `POST` create endpoint + 2 schema GET endpoints, inject new deps |
| `frontend/src/api/browse.js` | Add `createEntry`, `browseObjectClasses`, `browseObjectClassesBulk` |
| `frontend/src/components/CreateEntryForm.vue` | **New file** — schema-driven create form |
| `frontend/src/views/superadmin/DirectoryBrowserView.vue` | Add create mode toggle, button, form integration, tree refresh |
| `frontend/src/components/DnTree.vue` | Expose refresh/invalidate capability |

## What this does NOT include (future work)

- **Delete entry** — more dangerous, warrants its own iteration with confirmation dialogs
- **Edit existing entry** — modify attributes of an existing entry in-place
- **Schema caching** — schema is fetched fresh each time; caching can be added later
- **ACL checks** — superadmin bypasses all permission checks; no FeatureKey needed
