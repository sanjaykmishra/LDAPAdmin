# Provisioning Profiles — Architecture Plan

## Overview

Replace the current Realm + UserTemplate + RealmSetting + RealmObjectclass model with a unified **Provisioning Profile** concept that combines identity provisioning, access assignment, lifecycle policy, approval workflow, and form layout into a single manageable unit.

A provisioning profile answers: "What kind of user is this, where do they go, what do they get, and what rules apply?"

---

## Data Model

### ProvisioningProfile Entity

```
provisioning_profiles
├── id                          UUID, PK
├── directory_id                UUID, FK → directory_connections (required)
├── name                        VARCHAR(255), NOT NULL (e.g., "Full-Time Engineer")
├── description                 TEXT, nullable
├── target_ou_dn                VARCHAR(500), NOT NULL (e.g., "ou=engineers,ou=people,dc=corp")
├── object_class_names          TEXT[] (collection table: profile_object_classes)
├── rdn_attribute               VARCHAR(100), NOT NULL (e.g., "uid")
├── show_dn_field               BOOLEAN, default true
├── enabled                     BOOLEAN, default true
├── self_registration_allowed   BOOLEAN, default false
├── created_at                  TIMESTAMPTZ
├── updated_at                  TIMESTAMPTZ
└── UNIQUE(directory_id, name)
```

### ProfileAttributeConfig Entity

Replaces `UserTemplateAttributeConfig`. Adds default values, validation, computed expressions.

```
profile_attribute_configs
├── id                  UUID, PK
├── profile_id          UUID, FK → provisioning_profiles (required)
├── attribute_name      VARCHAR(100), NOT NULL (LDAP attribute)
├── custom_label        VARCHAR(255), nullable
├── input_type          VARCHAR(20), NOT NULL (TEXT, TEXTAREA, PASSWORD, BOOLEAN, DATE, DATETIME, MULTI_VALUE, DN_LOOKUP, SELECT, HIDDEN_FIXED)
├── required_on_create  BOOLEAN, default false
├── editable_on_create  BOOLEAN, default true
├── editable_on_update  BOOLEAN, default true
├── self_service_edit   BOOLEAN, default false
├── default_value       VARCHAR(500), nullable (static default)
├── computed_expression VARCHAR(500), nullable (e.g., "${givenName}.${sn}@corp.com")
├── validation_regex    VARCHAR(500), nullable (e.g., "^[a-z][a-z0-9-]{2,15}$")
├── validation_message  VARCHAR(255), nullable ("Must be 3-16 chars, lowercase alphanumeric")
├── allowed_values      TEXT, nullable (JSON array: ["Engineering","Finance","HR"])
├── min_length          INTEGER, nullable
├── max_length          INTEGER, nullable
├── section_name        VARCHAR(100), nullable
├── column_span         INTEGER, default 3 (1-3)
├── display_order       INTEGER, default 0
├── hidden              BOOLEAN, default false
└── UNIQUE(profile_id, attribute_name)
```

**New InputType values:**
- `SELECT` — dropdown populated from `allowed_values`
- `HIDDEN_FIXED` — not shown on form, always set to `default_value` (e.g., `employeeType = "contractor"`)

**Computed Expressions:**
Simple `${attributeName}` interpolation evaluated at creation time:
- `${givenName}.${sn}@corp.com` → `john.doe@corp.com`
- `/home/${uid}` → `/home/jdoe`
- `${givenName} ${sn}` → `John Doe`

Computed fields are shown as read-only on the form with a live preview as the user types referenced fields.

### ProfileGroupAssignment Entity

Auto-join groups on user creation.

```
profile_group_assignments
├── id              UUID, PK
├── profile_id      UUID, FK → provisioning_profiles (required)
├── group_dn        VARCHAR(500), NOT NULL
├── member_attribute VARCHAR(50), default 'member' (member, uniqueMember, memberUid)
├── display_order   INTEGER, default 0
└── UNIQUE(profile_id, group_dn)
```

### ProfileLifecyclePolicy Entity

Account lifecycle rules attached to a profile.

```
profile_lifecycle_policies
├── id                      UUID, PK
├── profile_id              UUID, FK → provisioning_profiles, UNIQUE (one policy per profile)
├── expires_after_days      INTEGER, nullable (account auto-expires N days after creation)
├── max_renewals            INTEGER, nullable (how many times expiration can be extended)
├── renewal_days            INTEGER, nullable (how many days each renewal adds)
├── on_expiry_action        VARCHAR(20), default 'DISABLE' (DISABLE, DELETE, MOVE)
├── on_expiry_move_dn       VARCHAR(500), nullable (target OU when action is MOVE)
├── on_expiry_remove_groups BOOLEAN, default true (remove from all groups on expiry)
├── on_expiry_notify        BOOLEAN, default true (email notification on expiry)
├── warning_days_before     INTEGER, nullable (send warning email N days before expiry)
└── created_at              TIMESTAMPTZ
```

### ProfileApprovalConfig Entity

Replaces realm settings for approval workflow.

```
profile_approval_configs
├── id                      UUID, PK
├── profile_id              UUID, FK → provisioning_profiles, UNIQUE
├── require_approval        BOOLEAN, default false
├── approver_mode           VARCHAR(20), default 'DATABASE' (DATABASE, LDAP_GROUP)
├── approver_group_dn       VARCHAR(500), nullable (when mode = LDAP_GROUP)
├── auto_escalate_days      INTEGER, nullable (escalate if no action in N days)
└── escalation_account_id   UUID, FK → accounts, nullable
```

### ProfileApprover Entity

Replaces `RealmApprover`.

```
profile_approvers
├── id              UUID, PK
├── profile_id      UUID, FK → provisioning_profiles (required)
├── admin_account_id UUID, FK → accounts (required)
└── UNIQUE(profile_id, admin_account_id)
```

### AdminProfileRole Entity

Replaces `AdminRealmRole`. Admins get roles per profile instead of per realm.

```
admin_profile_roles
├── id                  UUID, PK
├── admin_account_id    UUID, FK → accounts (required)
├── profile_id          UUID, FK → provisioning_profiles (required)
├── base_role           VARCHAR(20), NOT NULL (ADMIN, READ_ONLY)
├── created_at          TIMESTAMPTZ
├── updated_at          TIMESTAMPTZ
└── UNIQUE(admin_account_id, profile_id)
```

---

## Permission Model Changes

### Current: Four Dimensions
1. Realm access (AdminRealmRole)
2. Base role (ADMIN/READ_ONLY)
3. Feature override (per-admin feature keys)
4. Superadmin bypass

### New: Profile-Scoped Permissions

Replace realm-based access with profile-based access:

```
PermissionService.requireProfileAccess(principal, profileId)
  → Check AdminProfileRole exists for this admin + profile
  → Superadmin bypasses

PermissionService.requireDirectoryAccess(principal, directoryId)
  → Check admin has ANY profile role in the directory
  → Superadmin bypasses

PermissionService.requireFeature(principal, profileId, featureKey)
  → Check profile access + base role + feature override
  → Superadmin bypasses
```

**Feature keys scoped to profile:**
An admin with `USER_CREATE` permission and ADMIN role on the "Engineering Contractor" profile can create contractor users but cannot create "Full-Time Engineer" users unless they also have that profile role.

### User Visibility

When listing users, admins see users whose DN falls under the `target_ou_dn` of any profile they have access to. This replaces the realm `userBaseDn` scoping.

For overlapping target OUs (e.g., `ou=people` and `ou=contractors,ou=people`), the most specific match wins. Users not matching any profile's target OU are only visible to superadmins.

---

## Service Layer

### ProvisioningProfileService

Core CRUD + provisioning logic.

```java
public class ProvisioningProfileService {

    // CRUD
    List<ProfileResponse> list(UUID directoryId);
    ProfileResponse get(UUID profileId);
    ProfileResponse create(CreateProfileRequest req);
    ProfileResponse update(UUID profileId, UpdateProfileRequest req);
    void delete(UUID profileId);
    ProfileResponse clone(UUID profileId, String newName);

    // Provisioning
    LdapEntryResponse provisionUser(UUID profileId, Map<String, List<String>> attributes, AuthPrincipal principal);
    void deprovisionUser(UUID profileId, String userDn, AuthPrincipal principal);

    // Profile resolution
    Optional<ProvisioningProfile> resolveProfileForDn(UUID directoryId, String dn);
    List<ProvisioningProfile> getProfilesForAdmin(UUID adminAccountId);
}
```

### provisionUser() Flow

```
1. Load profile + attribute configs + group assignments + lifecycle policy
2. Validate required attributes are present
3. Evaluate computed expressions (${givenName}.${sn} → john.doe)
4. Apply default values for missing optional attributes
5. Apply fixed values (HIDDEN_FIXED input type)
6. Validate attribute values (regex, min/max length, allowed values)
7. Check approval requirement
   → If required: serialize to PendingApproval, return 202
   → If not required: continue
8. Build LDAP attribute map with object classes
9. Construct DN: rdn_attribute=value,target_ou_dn
10. Create LDAP entry via LdapUserService
11. Add user to auto-join groups (ProfileGroupAssignment)
12. If lifecycle policy exists: schedule expiration event
13. Audit log the creation with profile ID reference
14. Return created entry
```

### LifecycleScheduler

New scheduled job that processes lifecycle policies.

```java
@Scheduled(cron = "0 0 2 * * *")  // daily at 2am
public void processLifecyclePolicies() {
    // 1. Find users approaching expiry → send warning emails
    // 2. Find expired users → execute on_expiry_action (disable/delete/move)
    // 3. Remove expired users from groups if on_expiry_remove_groups = true
    // 4. Audit log all actions
}
```

---

## API Endpoints

### Profile Management (Superadmin)

```
GET    /api/v1/directories/{dirId}/profiles                     List profiles
POST   /api/v1/directories/{dirId}/profiles                     Create profile
GET    /api/v1/directories/{dirId}/profiles/{profileId}          Get profile
PUT    /api/v1/directories/{dirId}/profiles/{profileId}          Update profile
DELETE /api/v1/directories/{dirId}/profiles/{profileId}          Delete profile
POST   /api/v1/directories/{dirId}/profiles/{profileId}/clone    Clone profile
```

### Profile Attribute Config

```
GET    /api/v1/profiles/{profileId}/attributes                   List attribute configs
PUT    /api/v1/profiles/{profileId}/attributes                   Replace all configs
```

### Profile Group Assignments

```
GET    /api/v1/profiles/{profileId}/groups                       List auto-join groups
PUT    /api/v1/profiles/{profileId}/groups                       Replace group list
```

### Profile Lifecycle Policy

```
GET    /api/v1/profiles/{profileId}/lifecycle                    Get lifecycle policy
PUT    /api/v1/profiles/{profileId}/lifecycle                    Set lifecycle policy
DELETE /api/v1/profiles/{profileId}/lifecycle                    Remove lifecycle policy
```

### Profile Approval Config

```
GET    /api/v1/profiles/{profileId}/approval                     Get approval config
PUT    /api/v1/profiles/{profileId}/approval                     Set approval config
GET    /api/v1/profiles/{profileId}/approvers                    List approvers
PUT    /api/v1/profiles/{profileId}/approvers                    Set approvers
```

### User Provisioning

```
POST   /api/v1/directories/{dirId}/profiles/{profileId}/provision   Create user via profile
```

### Admin Profile Roles

```
GET    /api/v1/superadmin/accounts/{accountId}/profile-roles     List admin's profile roles
POST   /api/v1/superadmin/accounts/{accountId}/profile-roles     Assign profile role
DELETE /api/v1/superadmin/accounts/{accountId}/profile-roles/{profileId}  Remove profile role
```

---

## Frontend

### Superadmin Profile Management Page

**Route:** `/superadmin/profiles`
**Component:** `SuperadminProfilesView.vue`

Tab-based interface per profile with sections:

1. **General** — Name, description, directory, target OU, object classes, RDN attribute, enabled toggle, self-registration toggle
2. **Attributes** — Same attribute config table as current UserTemplatesView but with new columns: default value, computed expression, validation regex, allowed values, self-service editable
3. **Layout** — Reuse FormLayoutDesigner component for section/column organization
4. **Groups** — Auto-join group list with DN input and member attribute selector
5. **Lifecycle** — Expiration days, renewal config, on-expiry actions
6. **Approval** — Require approval toggle, approver mode, approver list
7. **Permissions** — Which admins have access to this profile (AdminProfileRole management)

### User Creation Flow Changes

Current:
```
Pick directory → Pick realm → (Pick template if multiple) → Fill form → Submit
```

New:
```
Pick directory → Pick profile → Fill form (pre-populated with defaults/computed) → Submit
```

The profile picker replaces both realm and template selection. The form renders dynamically from `ProfileAttributeConfig` with:
- Static defaults pre-filled
- Computed fields shown as read-only with live preview
- Fixed fields hidden from UI but included in submission
- Validation rules enforced client-side in real-time
- Auto-join groups shown as read-only info panel ("This user will be added to: vpn-users, github-access")

### Self-Registration Flow Changes

Current:
```
Pick directory → Pick realm → Hardcoded fields → Submit
```

New:
```
Pick directory → Pick profile (filtered to self_registration_allowed=true) → Dynamic form from profile → Submit
```

The registration form renders the same `ProfileAttributeConfig` fields, filtered to those where `editable_on_create = true`. Computed and fixed attributes are applied server-side during provisioning.

### Admin Account Management Changes

Current: Assign admin to realms with base role
New: Assign admin to profiles with base role

The admin management page shows a profile picker (grouped by directory) instead of a realm picker.

### Sidebar Navigation Changes

Current sidebar scopes to a directory + realm (implied by base DN):
```
Directory: Corporate LDAP
├── Users (scoped to realm's userBaseDn)
├── Groups (scoped to realm's groupBaseDn)
├── Audit
├── Bulk
├── Reports
├── Approvals
└── Access Reviews
```

New sidebar scopes to a directory; user list shows a profile filter:
```
Directory: Corporate LDAP
├── Users (profile filter dropdown in list view)
├── Groups
├── Audit
├── Bulk
├── Reports
├── Approvals
└── Access Reviews
```

The user list view gets a profile filter dropdown. Selecting "Full-Time Engineer" filters to users under that profile's target OU. "All" shows all users visible to the admin across their permitted profiles.

---

## Database Migration

### New Tables
- `provisioning_profiles`
- `profile_object_classes` (collection table)
- `profile_attribute_configs`
- `profile_group_assignments`
- `profile_lifecycle_policies`
- `profile_approval_configs`
- `profile_approvers`
- `admin_profile_roles`

### Dropped Tables
- `user_template`
- `user_template_object_classes`
- `user_template_attribute_config`
- `realm_objectclasses`
- `realms`
- `realm_settings`
- `realm_approvers`
- `admin_realm_roles`

### Migration Strategy

Since there are no existing deployments to migrate, this is a clean replacement. A single Flyway migration creates the new tables and drops the old ones.

---

## Implementation Phases

### Phase 1: Core Profile Entity & CRUD
- Create database migration with new tables
- Implement `ProvisioningProfile`, `ProfileAttributeConfig`, `ProfileGroupAssignment` entities
- Implement `ProvisioningProfileService` with CRUD operations
- Implement `ProvisioningProfileController` with REST endpoints
- Implement profile clone functionality
- Write unit and integration tests

### Phase 2: Provisioning Engine
- Implement `provisionUser()` flow: defaults, computed expressions, validation, LDAP creation, group auto-join
- Integrate approval workflow with `ProfileApprovalConfig`
- Implement `ProfileApprover` entity and service
- Update `UserController` to accept profile-based provisioning
- Update `ApprovalWorkflowService` to reference profiles instead of realms
- Write provisioning tests

### Phase 3: Permission Model
- Implement `AdminProfileRole` entity and repository
- Update `PermissionService` to check profile-based access
- Update `AdminManagementService` for profile role assignment
- Update `AdminManagementController` endpoints
- Write permission tests

### Phase 4: Lifecycle Policies
- Implement `ProfileLifecyclePolicy` entity
- Implement `LifecycleScheduler` for expiry processing
- Implement warning email notifications
- Implement on-expiry actions (disable, delete, move, remove groups)
- Write lifecycle tests

### Phase 5: Frontend — Profile Management
- Create `SuperadminProfilesView.vue` with tabbed interface
- Adapt `FormLayoutDesigner.vue` for profile attribute configs
- Add new attribute config fields (defaults, computed, validation, allowed values)
- Add group assignment management
- Add lifecycle policy configuration
- Add approval configuration

### Phase 6: Frontend — User Provisioning
- Update user creation flow to use profile picker
- Implement dynamic form rendering with defaults, computed fields, validation
- Show auto-join groups info panel
- Update self-registration to use profile-driven forms

### Phase 7: Frontend — Permissions & Navigation
- Update admin account management for profile roles
- Update sidebar navigation and user list with profile filtering
- Remove all realm references from UI

### Phase 8: Cleanup
- Remove old realm, user template, and realm setting entities, services, controllers, repositories
- Remove old database tables via migration
- Remove old frontend components and API clients
- Update all tests

---

## Entities to Remove

### Backend
- `Realm.java`
- `RealmSetting.java`
- `RealmApprover.java`
- `RealmObjectclass.java`
- `AdminRealmRole.java`
- `UserTemplate.java`
- `UserTemplateAttributeConfig.java`
- `RealmRepository.java`
- `RealmSettingRepository.java`
- `RealmApproverRepository.java`
- `RealmObjectclassRepository.java`
- `AdminRealmRoleRepository.java`
- `UserTemplateRepository.java`
- `UserTemplateAttributeConfigRepository.java`
- `RealmService.java`
- `RealmSettingService.java`
- `RealmApproverService.java`
- `UserTemplateService.java`
- `RealmController.java`
- `RealmSettingsController.java`
- `SuperadminRealmController.java`
- `UserTemplateController.java`

### Frontend
- `views/realms/RealmsView.vue`
- `views/userTemplates/UserTemplatesView.vue`
- `api/realms.js`
- `api/userTemplates.js`

### Tests
- `RealmServiceTest.java`
- `RealmControllerTest.java`
- `UserTemplateServiceTest.java`

---

## Key Design Decisions

1. **Profiles are directory-scoped.** Each profile belongs to exactly one directory connection. Cross-directory profiles are not supported — different directories have different schemas.

2. **Profile-based access replaces realm-based access.** Admins are granted roles per profile, not per DN subtree. This decouples "who can manage these users" from "where users live in the tree."

3. **One lifecycle policy per profile.** Keeps the model simple. A profile like "Contractor (90-day)" has exactly one expiry rule. Create a separate profile for different lifecycle needs.

4. **Computed expressions are simple interpolation, not a scripting language.** `${attributeName}` references only. No conditionals, no functions. This keeps the implementation secure and predictable. If more complex logic is needed later, it can be added as named functions (e.g., `${lower(uid)}`).

5. **Group auto-join is declarative, not conditional.** All users created with a profile get the same groups. Per-user group customization happens after creation via the group management UI.

6. **Self-registration is a profile flag.** Only profiles with `self_registration_allowed = true` appear in the public registration form. This replaces the realm-level self-registration setting.

7. **Profile overlap is allowed.** Two profiles can target the same OU with different object classes or provisioning rules (e.g., "Engineer" and "Engineering Manager" both targeting `ou=engineering`). When resolving a DN to a profile, object class matching is used as a tiebreaker.
