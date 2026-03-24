# First-Run Setup Wizard — Detailed Implementation Plan

## Goal

Replace the current 9-step manual onboarding with a guided 6-step wizard that runs on first superadmin login. The wizard creates one LDAP directory connection, one provisioning profile, and optionally one access review campaign — enough to make the product immediately useful.

The Discovery Wizard (DISCOVERY_WIZARD_PLAN.md) remains a separate feature, linked from Step 4 and the final summary as a power-user migration tool.

---

## Architecture Decisions

- **No new backend services.** The wizard frontend calls existing REST endpoints: `DirectoryConnectionService`, `ProvisioningProfileService`, `AccessReviewCampaignService`, `SchemaController`, and `ApplicationSettingsService`.
- **One new public endpoint.** `GET /api/v1/auth/setup-status` returns `{ setupCompleted: boolean }` without authentication, so the frontend can decide whether to show the wizard before login.
- **One Flyway migration.** Adds `setup_completed` boolean column to `application_settings`.
- **One new Vue view.** `SetupWizardView.vue` — a multi-step form that reuses existing API modules and the `GroupDnPicker` component.
- **Router guard.** After login, if `setupCompleted === false` and user is superadmin, redirect to `/setup`.

---

## Backend Changes

### 1. Flyway Migration V39

**File:** `src/main/resources/db/migration/V39__setup_completed_flag.sql`

```sql
ALTER TABLE application_settings
    ADD COLUMN setup_completed BOOLEAN NOT NULL DEFAULT FALSE;
```

### 2. Update ApplicationSettings Entity

**File:** `src/main/java/com/ldapadmin/entity/ApplicationSettings.java`

Add field after the SIEM section:

```java
// ── Setup wizard ──────────────────────────────────────────────────────
@Column(name = "setup_completed", nullable = false)
private boolean setupCompleted = false;
```

### 3. Update ApplicationSettingsDto

**File:** `src/main/java/com/ldapadmin/dto/settings/ApplicationSettingsDto.java`

Add `boolean setupCompleted` to the record parameters.

### 4. Update UpdateApplicationSettingsRequest

**File:** `src/main/java/com/ldapadmin/dto/settings/UpdateApplicationSettingsRequest.java`

Add `Boolean setupCompleted` to the record parameters (nullable — null means don't change).

### 5. Update ApplicationSettingsService.upsert()

**File:** `src/main/java/com/ldapadmin/service/ApplicationSettingsService.java`

In the `upsert` method, handle the new field:

```java
if (req.setupCompleted() != null) {
    settings.setSetupCompleted(req.setupCompleted());
}
```

In the `toDto` method, include `settings.isSetupCompleted()`.

In the `defaultDto` method, include `false`.

### 6. New Public Endpoint: Setup Status

**File:** `src/main/java/com/ldapadmin/controller/AuthController.java`

Add a new method to the existing AuthController:

```java
/**
 * Public endpoint — returns whether the first-run setup wizard has been completed.
 * Used by the frontend to decide whether to redirect to /setup after login.
 */
@GetMapping("/setup-status")
public Map<String, Boolean> setupStatus() {
    ApplicationSettings settings = settingsService.getEntity();
    return Map.of("setupCompleted", settings.isSetupCompleted());
}
```

This endpoint is unauthenticated (the path `/api/v1/auth/**` is already permitted in SecurityConfig).

### 7. Update SecurityConfig (if needed)

**File:** `src/main/java/com/ldapadmin/config/SecurityConfig.java`

Verify that `/api/v1/auth/setup-status` is included in the existing permit pattern for `/api/v1/auth/**`. If the pattern only covers specific auth paths, add this one explicitly.

---

## Frontend Changes

### 8. New API Module: Setup

**File:** `frontend/src/api/setup.js`

```javascript
import client from './client'

/** Public — no auth required */
export const getSetupStatus = () =>
  client.get('/auth/setup-status')
```

### 9. New View: SetupWizardView.vue

**File:** `frontend/src/views/SetupWizardView.vue`

A full-page multi-step wizard (not inside AppLayout — it has its own minimal chrome). Six steps, each rendered as a component section within a single SFC.

#### Step 1: Welcome

- App logo and name from settings store
- Brief product intro: "Let's get your LDAP directory connected in a few minutes."
- What we'll configure: connect a directory, verify it works, create a profile, optionally start an access review
- "Get Started" button → next step

#### Step 2: Connect LDAP Directory

Reuse the same form fields as `DirectoriesManageView.vue` but simplified (no pool tuning, no enable/disable attribute config — use defaults):

- **Display name** — text input (required)
- **Host** — text input (required)
- **Port** — number input (default 389)
- **SSL mode** — select: NONE / LDAPS / STARTTLS (default NONE)
- **Bind DN** — text input (required), placeholder: `cn=admin,dc=example,dc=com`
- **Bind password** — password input (required)
- **Base DN** — text input (required), placeholder: `dc=example,dc=com`
- **Trust all certificates** — checkbox (for lab/dev environments)
- **"Test Connection" button** — calls `testDirectory()` API, shows success (green, with response time) or error (red, with message)
- **"Save & Continue" button** — calls `createDirectory()` with sensible defaults for pool/paging, stores returned `directoryId` in wizard state

API calls:
- `POST /api/v1/superadmin/directories/test` (TestConnectionRequest)
- `POST /api/v1/superadmin/directories` (DirectoryConnectionRequest with defaults: pagingSize=500, poolMinSize=2, poolMaxSize=10, poolConnectTimeoutSeconds=10, poolResponseTimeoutSeconds=30, enabled=true)

#### Step 3: Verify Connection

Automatically runs on mount. Shows a summary card with live-fetched data:

- **Directory name** and connection details (read-only)
- **User count** — call `GET /api/v1/directories/{dirId}/users?filter=(objectClass=inetOrgPerson)&maxResults=5` and show count + sample entries (dn, cn)
- **Group count** — call `GET /api/v1/directories/{dirId}/groups?filter=(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup))&maxResults=5` and show count + sample entries (dn, cn)
- If counts are zero, show warning: "No entries found — check your base DN"
- **"Back" button** — return to step 2 to fix connection
- **"Continue" button** — proceed to profile creation

API calls:
- `GET /api/v1/directories/{dirId}/users` (existing UserController.search)
- `GET /api/v1/directories/{dirId}/groups` (existing GroupController.search)

#### Step 4: Create Profile

Simplified profile creation form. Uses schema discovery to suggest values.

On mount, fetch schema:
- `GET /api/v1/directories/{dirId}/schema/object-classes` → populate objectClass multi-select

Form fields:
- **Profile name** — text input (required), default: directory display name
- **Target OU** — text input (required), default: baseDn from step 2
- **Object classes** — multi-select dropdown populated from schema, default pre-select `inetOrgPerson` + `organizationalPerson` + `person` + `top` if they exist
- **RDN attribute** — text input, default `uid`
- **"Save & Continue" button** — calls `createProfile()` with defaults (enabled=true, showDnField=false, no group assignments, no attribute configs — those can be refined later)

**Discovery Wizard link:** Below the form, show a callout box:
> "Migrating an existing directory with multiple OUs? Use the **Discovery Wizard** to auto-generate profiles from your directory structure."
> [Launch Discovery Wizard →] (disabled/grayed with tooltip "Available after setup" — the discovery wizard route requires the directory to exist first; link becomes active post-setup)

API calls:
- `GET /api/v1/directories/{dirId}/schema/object-classes` (SchemaController)
- `POST /api/v1/directories/{dirId}/profiles` (ProvisioningProfileService.create via superadmin endpoint)

#### Step 5: First Access Review (Optional)

Optional step — user can skip to the final step.

- **Campaign name** — text input, default: "Initial Access Review"
- **Deadline** — number input (days), default: 30
- **Groups to review** — use `GroupDnPicker` component (already exists) to add 1+ groups, each with member attribute auto-detected
- **Reviewer** — pre-filled with current superadmin account (read-only for now, since this is first-run and there are no other admins)
- **"Skip" button** → go to step 6
- **"Create Campaign & Continue" button** → calls `createCampaign()`, stores campaignId

API calls:
- `POST /api/v1/directories/{dirId}/access-reviews` (CreateCampaignRequest)

CreateCampaignRequest needs:
```json
{
  "name": "Initial Access Review",
  "deadlineDays": 30,
  "autoRevoke": false,
  "autoRevokeOnExpiry": false,
  "groups": [
    { "groupDn": "cn=...", "memberAttribute": "member", "reviewerAccountId": "<current user id>" }
  ]
}
```

#### Step 6: Done

Summary of what was configured:
- Directory: "{name}" connected at {host}:{port} ✓
- Profile: "{name}" targeting {targetOuDn} ✓
- Access Review: "{name}" with {n} groups, deadline in {n} days ✓ (or "Skipped" if skipped)

Links:
- "Go to Dashboard" → `/superadmin/dashboard`
- "Manage Profiles" → `/superadmin/profiles`
- "View Campaign" → `/directories/{dirId}/access-reviews/{campaignId}` (if created)
- "Launch Discovery Wizard" → `/superadmin/directories/{dirId}/discover` (link to the separate discovery feature for users who want to auto-generate more profiles)

**"Complete Setup" button** → calls `PUT /api/v1/superadmin/settings` with `setupCompleted: true`, then navigates to dashboard.

API calls:
- `PUT /api/v1/superadmin/settings` (UpdateApplicationSettingsRequest with setupCompleted=true, other fields preserved from current settings)

### 10. Router Changes

**File:** `frontend/src/router/index.js`

Add the wizard route outside the AppLayout shell (wizard has its own minimal layout):

```javascript
// ── Setup Wizard ──────────────────────────────────────────────────────
{
  path: '/setup',
  name: 'setup',
  component: () => import('@/views/SetupWizardView.vue'),
  meta: { requiresSuperadmin: true },
},
```

### 11. Router Guard: Auto-redirect to Setup

**File:** `frontend/src/router/index.js`

In the existing `router.beforeEach` guard, after verifying the user is logged in and not self-service, add:

```javascript
// Redirect superadmin to setup wizard if first run
if (auth.isSuperadmin && to.name !== 'setup' && !to.meta.public) {
  if (auth.setupPending) {
    return { name: 'setup' }
  }
}
```

This requires the auth store to know the setup status.

### 12. Auth Store: Setup Status

**File:** `frontend/src/stores/auth.js`

Add `setupPending` state and check it during `init()`:

```javascript
// In state:
setupPending: false,

// In init(), after successful /me call:
if (principal.accountType === 'SUPERADMIN') {
  try {
    const { data } = await getSetupStatus()
    this.setupPending = !data.setupCompleted
  } catch { /* ignore — treat as completed */ }
}

// New method:
markSetupComplete() {
  this.setupPending = false
}
```

The wizard's final step calls `auth.markSetupComplete()` after the settings PUT succeeds, so the guard stops redirecting.

---

## Test Plan

### Backend Tests

#### 13. ApplicationSettingsServiceTest Update

**File:** `src/test/java/com/ldapadmin/service/ApplicationSettingsServiceTest.java`

Add tests:
- `upsert_setsSetupCompleted_whenProvided` — verify `setupCompleted=true` persists
- `upsert_preservesSetupCompleted_whenNull` — verify null doesn't overwrite
- `get_includesSetupCompleted_inDto` — verify DTO mapping

#### 14. AuthController Setup-Status Test

**File:** `src/test/java/com/ldapadmin/controller/AuthControllerTest.java`

Add test:
- `setupStatus_returnsSetupCompleted_unauthenticated` — verify 200 with `{"setupCompleted": false}` when no settings row exists
- `setupStatus_returnsTrue_afterSetupCompleted` — verify 200 with `{"setupCompleted": true}` after setup

### Frontend Tests

No frontend test runner exists yet — manual testing per the testing document.

---

## File Summary

### New Files (3)

| # | File | Purpose |
|---|------|---------|
| 1 | `src/main/resources/db/migration/V39__setup_completed_flag.sql` | Add `setup_completed` column |
| 2 | `frontend/src/api/setup.js` | Setup status API client |
| 3 | `frontend/src/views/SetupWizardView.vue` | 6-step setup wizard |

### Modified Files (8)

| # | File | Change |
|---|------|--------|
| 1 | `ApplicationSettings.java` | Add `setupCompleted` field |
| 2 | `ApplicationSettingsDto.java` | Add `setupCompleted` to record |
| 3 | `UpdateApplicationSettingsRequest.java` | Add `Boolean setupCompleted` |
| 4 | `ApplicationSettingsService.java` | Handle `setupCompleted` in upsert/toDto/defaultDto |
| 5 | `AuthController.java` | Add `GET /setup-status` endpoint |
| 6 | `frontend/src/router/index.js` | Add `/setup` route + guard redirect |
| 7 | `frontend/src/stores/auth.js` | Add `setupPending` state + `markSetupComplete()` |
| 8 | `SecurityConfig.java` | Verify `/auth/setup-status` is permitted (likely already covered) |

### Test Files (2 modified)

| # | File | Change |
|---|------|--------|
| 1 | `ApplicationSettingsServiceTest.java` | Add setupCompleted tests |
| 2 | `AuthControllerTest.java` | Add setup-status endpoint test |

### No Changes Needed

These existing components are reused as-is:
- `DirectoryConnectionService` / `DirectoryConnectionRequest` / `TestConnectionRequest`
- `ProvisioningProfileService` / `CreateProfileRequest`
- `AccessReviewCampaignService` / `CreateCampaignRequest`
- `SchemaController` / `LdapSchemaService`
- `GroupDnPicker.vue` component
- All existing API modules (`directories.js`, `profiles.js`, `accessReviews.js`, `schema.js`, `settings.js`)

---

## Implementation Order

| Phase | Work | Depends On | Scope |
|-------|------|------------|-------|
| **A** | Migration V39 + entity + DTO + service changes | — | 5 files, ~20 lines each |
| **B** | `GET /setup-status` endpoint + tests | A | 2 files |
| **C** | Auth store `setupPending` + router guard + `/setup` route | B | 2 files |
| **D** | `SetupWizardView.vue` Steps 1-2 (Welcome + Connect LDAP) | C | 1 file, ~200 lines |
| **E** | `SetupWizardView.vue` Step 3 (Verify Connection) | D | +~80 lines |
| **F** | `SetupWizardView.vue` Step 4 (Create Profile + Discovery link) | E | +~120 lines |
| **G** | `SetupWizardView.vue` Step 5 (Access Review, optional) | F | +~100 lines |
| **H** | `SetupWizardView.vue` Step 6 (Done + mark complete) | G | +~60 lines |
| **I** | Backend tests + manual testing doc | H | 2 test files + 1 doc |

Phases A-C are backend/infra and can be tested independently. Phases D-H build the wizard UI incrementally. Phase I validates everything.

---

## Discovery Wizard Integration Points

The Discovery Wizard (DISCOVERY_WIZARD_PLAN.md) is **not** built as part of this work. Two link points are wired:

1. **Step 4 callout:** "Migrating an existing directory? Launch the Discovery Wizard." — renders as a styled info box with a link to `/superadmin/directories/{dirId}/discover`. The link is only active if that route exists (graceful degradation: if the discovery wizard hasn't been built yet, the link navigates to a 404 or is hidden via a feature flag / route existence check).

2. **Step 6 summary:** "Want more profiles? Use the Discovery Wizard to auto-generate them from your directory structure." — same link pattern.

This approach keeps the setup wizard self-contained while providing a natural upgrade path for power users.
