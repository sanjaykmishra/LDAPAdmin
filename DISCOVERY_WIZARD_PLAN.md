# Directory Discovery Wizard — Implementation Plan

## Problem

When a customer migrates to LDAPAdmin from another LDAP management tool, their directory is already populated with users, groups, and OU structure. Today, they must manually create provisioning profiles, attribute configs, base DNs, and group assignments from scratch — a tedious and error-prone process for directories with many OUs and diverse user types.

## Solution

A **Discovery Wizard** that scans an existing LDAP directory and proposes a complete set of LDAPAdmin application constructs (profiles, attribute configs, base DNs, group assignments), which the admin reviews and refines before committing.

## Design Principles

- **Read-only scanning** — discovery never writes to LDAP; it only reads entries and schema
- **Propose, don't assume** — every inference is presented for review; the admin has final say
- **Leverage existing services** — `LdapSchemaService`, `LdapBrowseService`, `LdapUserService`, `LdapGroupService`, and `ProvisioningProfileService.create()` already provide the building blocks
- **Idempotent** — running discovery on a directory that already has profiles should detect and skip existing profiles (by `targetOuDn` match)

---

## Phase 1: Backend — `DirectoryDiscoveryService`

### 1a. New service

**File:** `src/main/java/com/ldapadmin/service/DirectoryDiscoveryService.java`

A stateless service that performs read-only scanning and returns a proposal DTO. No database writes — the admin commits the proposal via the existing `ProvisioningProfileService.create()` endpoint.

### 1b. Discovery algorithm

**Step 1 — Discover user OUs:**

```
Input:  directoryId, optional rootDn (defaults to baseDn)
Output: List<DiscoveredOU>
```

1. Start from `rootDn` (or each `DirectoryUserBaseDn` if configured)
2. Search one level for entries with `objectClass=organizationalUnit` (or `organizationalUnit`, `container`, `organization`)
3. For each OU, probe for person entries: `(&(objectClass=inetOrgPerson))` with `sizeLimit=1`
   - If an OU contains zero person entries, check its children recursively
   - If it contains person entries, record it as a user OU
4. For each user OU, sample up to 20 entries to collect:
   - The set of `objectClass` values present on entries (find the most common combination)
   - The RDN attribute (parse from DNs — e.g., `uid=jdoe,...` → `uid`)
   - The set of populated attribute names (for attribute config inference)
5. Return `List<DiscoveredOU>`:

```java
public record DiscoveredOU(
    String dn,
    String name,                              // parsed from RDN (e.g., "Staff" from ou=Staff)
    int userCount,                            // approximate count via sizeLimit search
    List<String> objectClasses,               // most common objectClass combination
    String rdnAttribute,                      // inferred from sampled DNs
    Set<String> populatedAttributes,          // attributes with values in sampled entries
    List<DiscoveredGroupLink> groupCandidates // groups whose members overlap this OU
) {}
```

**Step 2 — Discover group OUs:**

```
Input:  directoryId
Output: List<DiscoveredGroupOU>
```

1. Search for entries with `objectClass=groupOfNames` OR `objectClass=groupOfUniqueNames` OR `objectClass=posixGroup` under `baseDn`
2. Collect the distinct parent DNs of discovered groups → these are the group OUs
3. For each group, record: DN, cn, member attribute name (`member` vs `uniqueMember` vs `memberUid`), member count

```java
public record DiscoveredGroupOU(
    String dn,
    String name,
    int groupCount
) {}

public record DiscoveredGroup(
    String dn,
    String cn,
    String memberAttribute,
    int memberCount
) {}
```

**Step 3 — Infer attribute configs from schema:**

For each `DiscoveredOU`, use `LdapSchemaService.getAttributesForObjectClasses()` to get the MUST/MAY attributes, then for each attribute:

1. Call `LdapSchemaService.getAttributeTypeInfo()` to get syntax OID and single-valued flag
2. Infer `inputType` from syntax OID:

| Syntax OID | Syntax Name | Inferred InputType |
|------------|-------------|--------------------|
| `1.3.6.1.4.1.1466.115.121.1.7` | Boolean | BOOLEAN |
| `1.3.6.1.4.1.1466.115.121.1.15` | Directory String | TEXT |
| `1.3.6.1.4.1.1466.115.121.1.26` | IA5 String | TEXT |
| `1.3.6.1.4.1.1466.115.121.1.27` | Integer | TEXT |
| `1.3.6.1.4.1.1466.115.121.1.24` | Generalized Time | DATETIME |
| `1.3.6.1.4.1.1466.115.121.1.36` | Numeric String | TEXT |
| `1.3.6.1.4.1.1466.115.121.1.44` | Printable String | TEXT |
| `1.3.6.1.4.1.1466.115.121.1.50` | Telephone Number | TEXT |
| `1.3.6.1.4.1.1466.115.121.1.12` | DN | DN_LOOKUP |
| `1.3.6.1.4.1.1466.115.121.1.5` | Binary | *(skip — not form-editable)* |
| `1.3.6.1.4.1.1466.115.121.1.28` | JPEG | *(skip)* |
| `1.3.6.1.4.1.1466.115.121.1.40` | Octet String | TEXT |
| *(any other)* | | TEXT |

3. Override by well-known attribute name:

| Attribute Name | Override InputType |
|----------------|--------------------|
| `userPassword` | PASSWORD |
| `description`, `postalAddress` | TEXTAREA |
| `jpegPhoto` | *(skip)* |

4. If attribute is multi-valued (not `singleValued`) and inputType is TEXT → MULTI_VALUE
5. Set `requiredOnCreate = true` for MUST attributes
6. Set `editableOnCreate = true`, `editableOnUpdate = true` for all
7. Set `selfServiceEdit = false` for all (admin decides later)
8. If attribute is NOT in the `populatedAttributes` set from sampling → set `hidden = true` (suggest hiding rarely-used attributes)

```java
public record InferredAttributeConfig(
    String attributeName,
    String suggestedLabel,        // humanize: "telephoneNumber" → "Telephone Number"
    String inputType,             // inferred from schema
    boolean requiredOnCreate,     // from MUST/MAY
    boolean hidden,               // true if not populated in sampled entries
    boolean multiValued,          // from schema
    String syntaxOid              // for reference
) {}
```

**Step 4 — Cross-reference groups with user OUs:**

For each discovered group, sample its members (up to 50). For each member DN, check which `DiscoveredOU.dn` it falls under (suffix match). Build a mapping:

```
OU "ou=Staff,dc=example,dc=com" → [cn=vpn-users, cn=all-employees, cn=office365]
OU "ou=Contractors,dc=example,dc=com" → [cn=vpn-users, cn=contractor-access]
```

These become `groupCandidates` on each `DiscoveredOU` — the admin picks which to make auto-join groups.

**Step 5 — Assemble the proposal:**

```java
public record DiscoveryProposal(
    UUID directoryId,
    List<DiscoveredOU> userOUs,
    List<DiscoveredGroupOU> groupOUs,
    List<DiscoveredGroup> groups,
    List<ProposedProfile> profiles,
    List<String> warnings              // e.g., "No user entries found under ou=Archive"
) {}

public record ProposedProfile(
    String name,                       // from OU name
    String targetOuDn,
    List<String> objectClasses,
    String rdnAttribute,
    List<InferredAttributeConfig> attributeConfigs,
    List<DiscoveredGroupLink> groupCandidates,
    int estimatedUserCount
) {}

public record DiscoveredGroupLink(
    String groupDn,
    String groupCn,
    String memberAttribute,
    int overlapCount,                  // how many users in this OU are members
    double overlapPercent              // overlapCount / OU userCount
) {}
```

### 1c. Controller endpoint

**File:** `src/main/java/com/ldapadmin/controller/superadmin/DiscoveryController.java`

```
POST /api/v1/superadmin/directories/{directoryId}/discover
Body: { rootDn?: string, sampleSize?: int, includeGroups?: boolean }
Response: DiscoveryProposal
```

This is a read-only operation (POST because it may be slow and we don't want caching). The response contains the full proposal; the frontend renders it for review.

**Committing the proposal** uses the existing endpoints — the frontend calls `POST /api/v1/directories/{directoryId}/profiles` for each accepted profile, plus `PUT /api/v1/superadmin/directories/{directoryId}` to update base DNs.

### 1d. Request/Response DTOs

**File:** `src/main/java/com/ldapadmin/dto/discovery/DiscoveryRequest.java`

```java
public record DiscoveryRequest(
    String rootDn,           // null → use directory baseDn
    int sampleSize,          // default 20
    boolean includeGroups    // default true
) {}
```

**File:** `src/main/java/com/ldapadmin/dto/discovery/DiscoveryProposalResponse.java`

Contains the full proposal as described above, with nested records for each discovered element.

---

## Phase 2: Backend — Commit Helpers

### 2a. Bulk profile creation endpoint

Rather than forcing the frontend to call `createProfile` N times, add a bulk endpoint:

**File:** `src/main/java/com/ldapadmin/controller/superadmin/DiscoveryController.java`

```
POST /api/v1/superadmin/directories/{directoryId}/discover/commit
Body: CommitDiscoveryRequest
Response: CommitDiscoveryResponse
```

```java
public record CommitDiscoveryRequest(
    List<CreateProfileRequest> profiles,
    List<String> userBaseDns,
    List<String> groupBaseDns
) {}

public record CommitDiscoveryResponse(
    int profilesCreated,
    int userBaseDnsAdded,
    int groupBaseDnsAdded,
    List<String> warnings
) {}
```

This endpoint:
1. Creates all profiles via `ProvisioningProfileService.create()` in a single transaction
2. Adds `DirectoryUserBaseDn` entries for discovered user OUs (skipping duplicates)
3. Adds `DirectoryGroupBaseDn` entries for discovered group OUs (skipping duplicates)
4. Skips profiles whose `targetOuDn` already matches an existing profile

### 2b. Duplicate detection

Before creating, check `profileRepo.existsByDirectoryIdAndName()` and also check if any existing profile already has the same `targetOuDn`. Return warnings for skipped items.

---

## Phase 3: Frontend — Discovery Wizard UI

### 3a. New view

**File:** `frontend/src/views/superadmin/DiscoveryWizardView.vue`

A full-page wizard (not a modal — the proposal can be large). Route: `/superadmin/directories/:directoryId/discover`.

### 3b. Wizard steps

**Step 1 — Configure Scan**

- Show directory name and connection details (read-only)
- Optional: override root DN (defaults to baseDn)
- Sample size slider (10–50, default 20)
- "Include group analysis" toggle (default on)
- "Start Discovery" button → calls `POST .../discover`
- Loading state with progress indication ("Scanning OUs...", "Analyzing schema...", "Cross-referencing groups...")

**Step 2 — Review Discovered OUs**

- Table of discovered user OUs: DN, user count, inferred objectClasses, RDN attribute
- Checkbox per OU to include/exclude from profile creation
- Warning badges for OUs with few users or unusual objectClass combinations
- Discovered group OUs shown in a separate section

**Step 3 — Review Proposed Profiles**

For each included OU, show an expandable card:

- **Profile name** (editable text field, pre-filled from OU name)
- **Target OU DN** (read-only, from discovery)
- **Object classes** (pill tags, removable, with "add" from schema)
- **RDN attribute** (dropdown, pre-selected)
- **Attributes table** (sortable, filterable):
  - Checkbox: include in profile
  - Attribute name
  - Suggested label (editable)
  - Input type (dropdown: TEXT, TEXTAREA, PASSWORD, BOOLEAN, DATE, DATETIME, MULTI_VALUE, DN_LOOKUP, SELECT, HIDDEN_FIXED)
  - Required on create (checkbox)
  - Hidden (checkbox — pre-checked for attributes not found in sampled entries)
- Quick actions: "Include all", "Include populated only", "Reset to defaults"

**Step 4 — Review Group Assignments**

For each proposed profile, show candidate groups:

- Group CN, DN, member attribute
- Overlap indicator (e.g., "85% of Staff users are members")
- Checkbox to include as auto-join group
- Only show groups with >0% overlap; sort by overlap descending

**Step 5 — Review & Commit**

Summary view:

- N profiles to create (list names + target OUs)
- N user base DNs to add
- N group base DNs to add
- N group assignments total
- N attributes total across all profiles
- "Commit" button → calls `POST .../discover/commit`
- Success: redirect to `/superadmin/profiles` with success notification
- Partial failure: show per-profile status (created / skipped / error)

### 3c. API client

**File:** `frontend/src/api/discovery.js`

```javascript
import client from './client'

export const discoverDirectory = (directoryId, options) =>
    client.post(`/superadmin/directories/${directoryId}/discover`, options)

export const commitDiscovery = (directoryId, data) =>
    client.post(`/superadmin/directories/${directoryId}/discover/commit`, data)
```

### 3d. Router

Add to superadmin routes in `frontend/src/router/index.js`:

```javascript
{
  path: '/superadmin/directories/:directoryId/discover',
  name: 'discoveryWizard',
  component: () => import('@/views/superadmin/DiscoveryWizardView.vue'),
  meta: { requiresSuperadmin: true },
}
```

### 3e. Entry point

Add a "Discover & Import" button to `DirectoriesManageView.vue` in each directory's action row (next to Edit / Delete). This navigates to `/superadmin/directories/:id/discover`.

---

## Phase 4: Edge Cases & Polish

### 4a. Large directories

- The discovery scan uses `sizeLimit` on probes (not full subtree enumeration)
- User counting uses a limited search (`sizeLimit=1001`) — if >1000, report "1000+"
- Group member sampling caps at 50 members per group
- The wizard shows a warning if the directory has >50 OUs: "Large directory detected — consider narrowing the root DN"

### 4b. Active Directory support

AD uses different conventions:
- User objectClass: `user` instead of `inetOrgPerson`
- Group objectClass: `group` instead of `groupOfNames`
- RDN attribute: `cn` instead of `uid`
- Member attribute: `member` (same)
- Containers: `CN=Users` (not an OU)

The discovery algorithm handles this by:
1. Probing for both `inetOrgPerson` and `user` objectClasses
2. Probing for `groupOfNames`, `groupOfUniqueNames`, `posixGroup`, and `group`
3. Detecting `container` entries in addition to `organizationalUnit`
4. Letting the objectClass combination come from sampling, not assumptions

### 4c. Re-running discovery

If profiles already exist for some OUs, the wizard:
- Shows existing profiles grayed out with a "Already configured" badge
- Allows re-scanning to find new OUs added since last discovery
- The commit endpoint skips duplicates and reports them in the response

### 4d. Empty directory

If no user entries are found:
- Show a clear message: "No user entries found under [baseDn]"
- Suggest checking the base DN configuration
- Offer to create a blank profile manually (link to profile creation)

---

## Security

| Concern | Mitigation |
|---------|------------|
| Authorization | Discovery endpoints require `SUPERADMIN` role |
| Read-only scanning | Discovery never writes to LDAP; only `searchEntries` and `getSchema` calls |
| Sampling limits | `sampleSize` capped at 50 server-side to prevent excessive LDAP load |
| No credential exposure | Discovery doesn't read or return `userPassword` or other sensitive attributes |
| Rate limiting | Discovery is expensive — debounce in UI, consider a cooldown on the endpoint |

---

## File Summary

### New files (6)

| # | File | Purpose |
|---|------|---------|
| 1 | `DirectoryDiscoveryService.java` | Scanning logic, schema inference, group cross-referencing |
| 2 | `DiscoveryController.java` | REST endpoints for discover + commit |
| 3 | `DiscoveryRequest.java` | Request DTO |
| 4 | `DiscoveryProposalResponse.java` | Response DTO with nested records |
| 5 | `frontend/src/api/discovery.js` | API client |
| 6 | `frontend/src/views/superadmin/DiscoveryWizardView.vue` | Wizard UI |

### Modified files (2)

| File | Change |
|------|--------|
| `frontend/src/router/index.js` | Add discovery wizard route |
| `DirectoriesManageView.vue` | Add "Discover & Import" button per directory |

### Reused existing services (no changes needed)

| Service | Usage |
|---------|-------|
| `LdapSchemaService` | `getObjectClassNames`, `getAttributesForObjectClasses`, `getAttributeTypeInfo` |
| `LdapBrowseService` | `searchEntries` (one-level OU enumeration) |
| `LdapUserService` | `searchUsers` (sampling entries per OU) |
| `LdapGroupService` | `searchGroups`, `getMembers` (group discovery + member sampling) |
| `ProvisioningProfileService` | `create` (committing proposed profiles) |
| `DirectoryConnectionService` | Adding base DNs on commit |

---

## Implementation Order

1. **Phase 1** — `DirectoryDiscoveryService` + DTOs + controller endpoint (the core scanning logic)
2. **Phase 2** — Commit endpoint with bulk profile creation and duplicate detection
3. **Phase 3a-3b** — Wizard view (steps 1-2: scan config + OU review)
4. **Phase 3b-3c** — Wizard view (steps 3-5: profile review, group assignments, commit)
5. **Phase 3d-3e** — Router + entry point in directory management UI
6. **Phase 4** — Edge cases (AD support, large directories, re-running)

Phases 1-2 can be tested independently via API before the wizard UI is built.
