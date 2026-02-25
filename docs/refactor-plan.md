# Refactor Plan

## Overview

This plan removes multi-tenancy, replaces the branch-scoped attribute profile model with a per-objectclass model, unifies the two account tables into one, and introduces local (Postgres) admin authentication alongside the existing LDAP option — all governed by a new application settings auth page.

---

## 1. Database Changes

### 1.1 Drop Multi-Tenancy

**Remove `tenant_id` from every table and drop the `tenants` root table.**

Affected tables (all lose their `tenant_id` FK column):
- `directory_connections`
- `audit_data_sources`
- `attribute_profiles` *(being replaced — see §1.4)*
- `csv_mapping_templates`
- `scheduled_report_jobs`
- `application_settings`
- `audit_events`

Drop entirely:
- `tenant_auth_configs` — replaced by auth config in `application_settings` (§1.5)
- `tenants`

**Migration:** `V13__remove_tenancy.sql`

---

### 1.2 Unify Account Tables

Merge `superadmin_accounts` and `admin_accounts` into a single `accounts` table.

```sql
CREATE TABLE accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username            VARCHAR(255) NOT NULL UNIQUE,
    display_name        VARCHAR(255),
    email               VARCHAR(255),
    role                VARCHAR(20)  NOT NULL CHECK (role IN ('SUPERADMIN', 'ADMIN')),
    auth_type           VARCHAR(10)  NOT NULL DEFAULT 'LOCAL'
                            CHECK (auth_type IN ('LOCAL', 'LDAP')),
    password_hash       VARCHAR(255),           -- LOCAL accounts only
    ldap_dn             VARCHAR(500),           -- LDAP-sourced accounts only
    active              BOOLEAN      NOT NULL DEFAULT true,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

- Migrate existing superadmin rows with `role = 'SUPERADMIN'`.
- Migrate existing admin rows with `role = 'ADMIN'`, `auth_type = 'LOCAL'` (no password yet; password_hash NULL until set).
- Update FK columns on `admin_directory_roles`, `admin_branch_restrictions`, `admin_feature_permissions`, `scheduled_report_jobs`, and `audit_events` to reference `accounts(id)`.
- Drop `superadmin_accounts` and `admin_accounts`.

**Migration:** `V14__unify_accounts.sql`

---

### 1.3 Add Editable Flag to Directory User Branches

```sql
ALTER TABLE directory_user_base_dns
    ADD COLUMN editable BOOLEAN NOT NULL DEFAULT false;
```

Admins configure each user OU/branch individually — some branches are read-only (browsable) while others are writable (users can be created/moved there).

**Migration:** `V15__directory_branch_editable.sql`

---

### 1.4 Replace Attribute Profiles with Per-Objectclass Attribute Config

**Drop** `attribute_profile_entries` and `attribute_profiles` (branch-scoped, replaced entirely).

**Create** `directory_objectclasses` — the set of LDAP objectclasses permitted for user entries in a directory:

```sql
CREATE TABLE directory_objectclasses (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id      UUID NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    object_class_name VARCHAR(255) NOT NULL,
    display_name      VARCHAR(255),
    display_order     INT NOT NULL DEFAULT 0,
    UNIQUE (directory_id, object_class_name)
);
```

**Create** `objectclass_attribute_configs` — per-attribute behaviour within an objectclass:

```sql
CREATE TABLE objectclass_attribute_configs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    objectclass_id    UUID NOT NULL REFERENCES directory_objectclasses(id) ON DELETE CASCADE,
    attribute_name    VARCHAR(255) NOT NULL,
    custom_label      VARCHAR(255),
    required_on_create BOOLEAN NOT NULL DEFAULT false,
    editable_on_edit  BOOLEAN NOT NULL DEFAULT true,
    input_type        VARCHAR(20)  NOT NULL DEFAULT 'TEXT'
                          CHECK (input_type IN
                              ('TEXT','TEXTAREA','PASSWORD','BOOLEAN',
                               'DATE','DATETIME','MULTI_VALUE','DN_LOOKUP')),
    display_order     INT NOT NULL DEFAULT 0,
    visible_in_list   BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (objectclass_id, attribute_name)
);
```

Also remove the `object_classes` string column from `directory_connections` — objectclasses are now managed through `directory_objectclasses`.

**Migration:** `V16__objectclass_attribute_config.sql`

---

### 1.5 Add Auth Config to Application Settings

Replace `tenant_auth_configs` with global auth configuration stored in `application_settings`.

```sql
ALTER TABLE application_settings
    ADD COLUMN admin_auth_type            VARCHAR(10) NOT NULL DEFAULT 'LOCAL'
                                              CHECK (admin_auth_type IN ('LOCAL', 'LDAP')),
    ADD COLUMN ldap_auth_host             VARCHAR(255),
    ADD COLUMN ldap_auth_port             INT,
    ADD COLUMN ldap_auth_ssl_mode         VARCHAR(10)
                                              CHECK (ldap_auth_ssl_mode IN ('NONE','LDAPS','STARTTLS')),
    ADD COLUMN ldap_auth_trust_all_certs  BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN ldap_auth_trusted_cert_pem TEXT,
    ADD COLUMN ldap_auth_bind_dn          VARCHAR(500),
    ADD COLUMN ldap_auth_bind_password_enc TEXT,
    ADD COLUMN ldap_auth_user_search_base VARCHAR(500),
    ADD COLUMN ldap_auth_bind_dn_pattern  VARCHAR(500);
```

`ldap_auth_bind_dn_pattern` uses `{username}` substitution (e.g. `uid={username},ou=people,dc=example,dc=com`). The service will attempt a bind using the constructed DN and the supplied password.

`application_settings` now has exactly one row (singleton, no tenant scope).

**Migration:** `V17__settings_auth_config.sql`

---

### Summary of Migrations

| # | File | What it does |
|---|------|-------------|
| V13 | `remove_tenancy.sql` | Drop `tenant_id` columns, `tenant_auth_configs`, `tenants` |
| V14 | `unify_accounts.sql` | Create `accounts`, migrate data, drop old account tables, update FKs |
| V15 | `directory_branch_editable.sql` | Add `editable` to `directory_user_base_dns` |
| V16 | `objectclass_attribute_config.sql` | Drop attribute profiles, create objectclass tables, drop `object_classes` column |
| V17 | `settings_auth_config.sql` | Add auth config columns to `application_settings` |

---

## 2. Backend Changes

### 2.1 Remove Tenant Layer

- **Delete** `Tenant` entity, `TenantRepository`, `TenantService`, `TenantController`.
- **Delete** `TenantAuthConfig` entity, `TenantAuthConfigRepository`.
- Remove all `tenant` fields and constructor parameters from every remaining entity.
- Update `@Query` and JPQL in repositories that previously filtered by `tenantId`.
- Remove `tenantId` from `AuthPrincipal` and JWT claims.
- Remove `tenantSlug` from the login request DTO.

---

### 2.2 Unify Account Entities

- **Delete** `SuperadminAccount`, `AdminAccount` entities and their repositories.
- **Create** `Account` entity mapping the new `accounts` table.
  - Fields: `id`, `username`, `displayName`, `email`, `role` (enum: `SUPERADMIN`/`ADMIN`), `authType` (enum: `LOCAL`/`LDAP`), `passwordHash`, `ldapDn`, `active`, `lastLoginAt`.
- **Create** `AccountRepository` with finders: `findByUsername`, `findByRole`, `findAllByRole`.
- Update `AdminDirectoryRole`, `AdminBranchRestriction`, `AdminFeaturePermission`, `ScheduledReportJob`, and `AuditEvent` to reference `Account` instead of the old account entities.
- **Rename/merge** `AdminManagementService` and `SuperadminManagementService` into a single `AccountService` with methods scoped by `role`.

---

### 2.3 Update Authentication

Update `AuthenticationService.login(username, password)`:

1. Look up `Account` by username.
2. If `account.authType == LOCAL`: verify `password` against `account.passwordHash` (bcrypt).
3. If `account.authType == LDAP`: load LDAP auth config from `ApplicationSettings`; construct bind DN from `ldap_auth_bind_dn_pattern`; attempt LDAP bind against the configured server.
4. On success generate JWT with `accountId` and `role` claims (no `tenantId`).

Update `JwtTokenService` to issue/validate tokens without tenant context.

Update `AuthPrincipal` — fields: `id`, `username`, `role`.

---

### 2.4 Account Management — Support Local Password

Add to `AccountService`:

- `createAccount(username, displayName, email, role, authType, password?)` — hashes password with bcrypt when `authType == LOCAL`.
- `setPassword(accountId, newPassword)` — updates `password_hash`.
- `updateAccount(...)` — allows changing `role`, `authType`, `active`.

Expose via `AccountController` (`/api/v1/accounts`):

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/accounts` | List all accounts (with optional `?role=` filter) |
| POST | `/api/v1/accounts` | Create account |
| GET | `/api/v1/accounts/{id}` | Get account |
| PUT | `/api/v1/accounts/{id}` | Update account |
| DELETE | `/api/v1/accounts/{id}` | Delete account |
| PUT | `/api/v1/accounts/{id}/password` | Set local password |
| GET | `/api/v1/accounts/{id}/permissions` | Get directory roles + feature permissions |
| PUT | `/api/v1/accounts/{id}/permissions` | Replace directory roles + feature permissions |

Remove the old `SuperadminController` and `AdminManagementController`.

---

### 2.5 Directory Objectclasses & Attribute Config

**New entities:**

- `DirectoryObjectclass` — maps `directory_objectclasses`; fields: `id`, `directory`, `objectClassName`, `displayName`, `displayOrder`.
- `ObjectclassAttributeConfig` — maps `objectclass_attribute_configs`; fields: `id`, `objectclass`, `attributeName`, `customLabel`, `requiredOnCreate`, `editableOnEdit`, `inputType` (enum), `displayOrder`, `visibleInList`.

**New repositories:** `DirectoryObjectclassRepository`, `ObjectclassAttributeConfigRepository`.

**New service:** `ObjectclassService`
- `listObjectclasses(directoryId)`
- `createObjectclass(directoryId, objectClassName, displayName, displayOrder)`
- `updateObjectclass(...)`
- `deleteObjectclass(objectclassId)` — cascades to attribute configs
- `listAttributeConfigs(objectclassId)`
- `saveAttributeConfigs(objectclassId, List<AttributeConfigDto>)` — full replace

**New controller:** `ObjectclassController` nested under `/api/v1/directories/{directoryId}/objectclasses`

| Method | Path | Description |
|--------|------|-------------|
| GET | `.../objectclasses` | List objectclasses for directory |
| POST | `.../objectclasses` | Add objectclass |
| PUT | `.../objectclasses/{id}` | Update objectclass |
| DELETE | `.../objectclasses/{id}` | Delete objectclass |
| GET | `.../objectclasses/{id}/attributes` | List attribute configs |
| PUT | `.../objectclasses/{id}/attributes` | Replace attribute configs |

**Remove** `AttributeProfileService`, `AttributeProfileController`, `AttributeProfile`, `AttributeProfileEntry`, and their repositories.

**Update** `UserController` / `LdapOperationService` — when building the user creation form, return the attribute configs for the objectclass the user selects. When creating a user, validate required attributes are present.

---

### 2.6 Directory User Branches — Editable Flag

Update `DirectoryUserBaseDn` entity: add `editable` boolean field.

Update `DirectoryConnectionService` / `LdapOperationService`:
- When creating or moving a user, verify the target branch has `editable = true`.
- When listing branches in the user-creation form, only show editable branches in the "target OU" selector.

---

### 2.7 Application Settings — Auth Config

Extend `ApplicationSettings` entity with new auth config fields (matching V17 columns).

Extend `ApplicationSettingsService`:
- `getAuthConfig()` — returns admin auth type + LDAP details (with password masked).
- `saveAuthConfig(AdminAuthConfigDto)` — encrypts the LDAP bind password via `EncryptionService` before persisting.
- When `admin_auth_type` is `LDAP`, validate the LDAP connection can be established (test-bind) before saving.

Add `AdminAuthConfigDto` (request/response DTO):
```
adminAuthType, ldapAuthHost, ldapAuthPort, ldapAuthSslMode, ldapAuthTrustAllCerts,
ldapAuthTrustedCertPem, ldapAuthBindDn, ldapAuthBindPassword (write-only),
ldapAuthUserSearchBase, ldapAuthBindDnPattern
```

Update `ApplicationSettingsController` — add:
- `GET  /api/v1/settings/auth` → returns auth config (SUPERADMIN only)
- `PUT  /api/v1/settings/auth` → saves auth config (SUPERADMIN only)

---

### 2.8 Bootstrap & Singleton Settings

Update `BootstrapService`:
- Create the one `accounts` row (SUPERADMIN, LOCAL, with hashed password from env) on first startup.
- Ensure `application_settings` has exactly one row on startup (create with defaults if absent).

---

### 2.9 Audit Events

- Remove `actor_type` enum distinction (no longer ADMIN vs SUPERADMIN in separate tables) — replace with a single `role` string derived from the account.
- Remove `tenant_id` column and all tenant-scoped queries from `AuditEventRepository`.

---

### 2.10 Permission Model

No structural changes to `admin_directory_roles`, `admin_branch_restrictions`, or `admin_feature_permissions` beyond updating the FK to reference `accounts(id)`.

`PermissionService` is updated to load `Account` by id and check `account.role == SUPERADMIN` for superadmin bypass (superadmins retain full access to all features).

---

## 3. Frontend Changes

### 3.1 Remove Tenant UI

- Delete `frontend/src/views/superadmin/TenantsView.vue`.
- Remove tenant-related routes from `router/index.js`.
- Remove `tenantSlug` field from `LoginView.vue` and the login API call.
- Remove `tenantId` from the Pinia `auth` store.

---

### 3.2 Unified Account Management

Replace the separate superadmin and admin management views with a single **Accounts** section.

**New view:** `views/accounts/AccountListView.vue`
- Table with columns: Username, Display Name, Email, Role (badge), Auth Type, Active, Last Login, Actions.
- Filter by Role (All / Superadmin / Admin).
- Buttons: Add Account, Edit, Delete, Set Password.

**New view:** `views/accounts/AccountFormView.vue` (create & edit)
- Fields: Username, Display Name, Email.
- **Role** selector: Admin / Superadmin.
- **Auth Type** selector: Local (Postgres) / LDAP-sourced.
  - If Local: Password + Confirm Password fields (shown on create; "Set Password" button on edit).
  - If LDAP-sourced: LDAP DN field.
- Active toggle.
- Directory Permissions section (see §3.4).
- Feature Permissions section.

---

### 3.3 Directory Configuration — Objectclasses & Editable Branches

**Update** `DirectoryForm.vue`:
- Remove the free-text `objectClasses` field.
- Add an **Objectclasses** sub-section: a list of objectclass rows (objectclass name, display name, reorder handle, delete button) with an "Add objectclass" row.
- For each objectclass row, expand to show its **Attribute Configuration** table:
  - Columns: Attribute Name, Custom Label, Required on Create, Editable on Edit, Input Type, Display Order, Visible in List.
  - Inline editable rows; save the full set on directory save.

**Update** `DirectoryForm.vue` — User Branches tab:
- Add **Editable** toggle column to the branch list.

---

### 3.4 Admin Permission UI (within AccountFormView)

Reuse or adapt the existing permission components (previously in tenant admin management):

- **Directory Roles** card: directory selector → Base Role (Admin / Read-Only).
- **Branch Restrictions** card (per directory): list of branch DNs the account is allowed to manage. Empty = full directory access.
- **Feature Permissions** card: grid of 12 feature toggles.

---

### 3.5 Settings Page — Auth Config Tab

**Update** `SettingsView.vue` — add an **Authentication** tab alongside existing Branding / SMTP / S3 tabs.

Auth tab content:
- **Admin Authentication Method** radio: `Local (Postgres)` / `LDAP Server`.
- When `LDAP Server` selected, show:
  - Host, Port
  - SSL Mode (None / LDAPS / STARTTLS)
  - Trust All Certificates toggle + Certificate PEM textarea (conditional)
  - Bind DN (service account for user lookup, optional)
  - Bind Password
  - User Search Base DN
  - Bind DN Pattern (`uid={username},ou=people,dc=example,dc=com`)
  - "Test Connection" button → calls a backend endpoint that attempts an anonymous or service-account bind.
- Save button.

---

### 3.6 User Creation — Objectclass Selection

**Update** `UserForm.vue` (create mode):

1. **Objectclass selector** — dropdown populated from `GET /api/v1/directories/{id}/objectclasses`.
2. On objectclass selection, load `GET /api/v1/directories/{id}/objectclasses/{ocId}/attributes` and render the attribute form dynamically, respecting `requiredOnCreate`, `inputType`, and `customLabel`.
3. **Target OU** selector — populated only from branches where `editable = true`.

---

### 3.7 API Client Updates

- Add `frontend/src/api/accounts.js` — CRUD + password + permissions endpoints.
- Add `frontend/src/api/objectclasses.js` — objectclass + attribute config endpoints.
- Update `frontend/src/api/directories.js` — add branch editable flag.
- Update `frontend/src/api/settings.js` — add auth config get/save.
- Remove `frontend/src/api/superadmin.js` (superseded by accounts.js).
- Update `frontend/src/stores/auth.js` — remove `tenantId`, add `role`.

---

## 4. Files to Delete

| Path | Reason |
|------|--------|
| `entity/Tenant.java` | Tenancy removed |
| `entity/TenantAuthConfig.java` | Replaced by settings auth config |
| `entity/SuperadminAccount.java` | Merged into Account |
| `entity/AdminAccount.java` | Merged into Account |
| `entity/AttributeProfile.java` | Replaced by objectclass model |
| `entity/AttributeProfileEntry.java` | Replaced by objectclass model |
| `repository/TenantRepository.java` | — |
| `repository/TenantAuthConfigRepository.java` | — |
| `repository/SuperadminAccountRepository.java` | — |
| `repository/AdminAccountRepository.java` | — |
| `repository/AttributeProfileRepository.java` | — |
| `repository/AttributeProfileEntryRepository.java` | — |
| `service/TenantService.java` | — |
| `service/SuperadminManagementService.java` | Merged into AccountService |
| `service/AttributeProfileService.java` | — |
| `controller/TenantController.java` | — |
| `controller/SuperadminController.java` | — |
| `controller/AdminManagementController.java` | Replaced by AccountController |
| `controller/AttributeProfileController.java` | — |
| `frontend/src/views/superadmin/TenantsView.vue` | — |
| `frontend/src/api/superadmin.js` | — |

---

## 5. Implementation Order

Execute in this sequence to keep the application buildable at each step:

1. **V13–V17 database migrations** — schema foundation for all subsequent code changes.
2. **Account entity + repository + service + controller** — establishes the new auth/account model.
3. **Update AuthenticationService + JWT** — login works again against unified accounts table.
4. **Update PermissionService + AuditEvent** — remove tenant references, fix FK targets.
5. **DirectoryObjectclass + ObjectclassAttributeConfig** (entity, repo, service, controller).
6. **Update DirectoryUserBaseDn** entity + LdapOperationService editable-branch checks.
7. **Update ApplicationSettings** entity + service + controller (auth config endpoints).
8. **Remove deleted entities/services/controllers** — clean up after all replacements are in place.
9. **Frontend — Accounts section** (AccountListView, AccountFormView, accounts.js).
10. **Frontend — Settings auth tab** (SettingsView update, settings.js update).
11. **Frontend — Directory form** (objectclasses sub-section, editable branch toggle).
12. **Frontend — UserForm** (objectclass selector, dynamic attribute form, editable OU selector).
13. **Frontend — LoginView cleanup** (remove tenantSlug), router cleanup, auth store cleanup.
14. **Update ERD** to reflect the final schema.
