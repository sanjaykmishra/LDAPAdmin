# Updated Authorization Analysis

**Date:** 2026-03-23
**Supersedes:** CONSOLIDATED_AUTH_ANALYSIS.md
**Basis:** Code review of all files referenced in the original analysis

---

## Status Summary

Of the 14 findings in the original analysis, **12 are fully fixed**, **1 is a documented product decision** (not a bug), and **1 is minor dead code**. All five escalation chains are closed.

| Finding | Severity | Status | Notes |
|---------|----------|--------|-------|
| **C1** | Critical | **FIXED** | `@PreAuthorize("hasRole('SUPERADMIN')")` on both `get()` and `upsert()` in `ApplicationSettingsController` |
| **C2** | Critical | **FIXED** | `checkAndSubmitForApproval` now routes unprovisioned OUs to directory-level approval with null profileId instead of skipping approval |
| **C3** | Critical | **FIXED** | `LifecyclePlaybookService.execute()` checks `pb.isRequireApproval()` and routes to `PLAYBOOK_EXECUTE` approval type |
| **H1** | High | **FIXED** | Both single and bulk group-member-add go through `checkAndSubmitForApproval` |
| **H2** | High | **FIXED** | Original requester blocked from `updatePayload()` on own request; schema validation added; payload diffs audited |
| **H3** | High | **FIXED** | `@Version` on `PendingApproval` entity provides optimistic locking against concurrent approval |
| **H4** | High | **OPEN (minor)** | `REPORTS_EXPORT` feature key never existed in the enum — dead reference in original analysis, not an actual code gap |
| **H5** | High | **FIXED** | `generatePassword` endpoint calls `requireProfileAccess` |
| **M1** | Medium | **FIXED** | Audit log queries filtered by `getAuthorizedDirectoryIds()` for non-superadmins |
| **M2** | Medium | **FIXED** | `listReviewers` filters to admins with profile roles in the given directory |
| **M3** | Medium | **FIXED** | `AccessReviewController.create()` uses `@PreAuthorize("hasRole('SUPERADMIN')")` (annotation, not runtime check) |
| **M4** | Medium | **BY DESIGN** | Destructive ops (delete, disable, password reset) intentionally skip approval — documented in code comments referencing M4 |
| **M5** | Medium | **FIXED** | Bulk group import goes through `checkAndSubmitForApproval`, consistent with `BulkUserController` |

### Additional fixes applied (not in original analysis)

| Location | Fix |
|----------|-----|
| `UserController.create()` | `requireProfileAccess` added before `applyDefaults()` — prevents profile config leakage across profile boundaries |
| `ApprovalWorkflowService.checkAndSubmitForApproval()` | `requireProfileAccess` added after profile resolution — enforces profile scope regardless of whether approval is configured |
| `ApprovalWorkflowService.executeUserCreate()` | `requireProfileAccess` on approver before execution |
| `ApprovalWorkflowService.executeBulkImport()` | Same |
| `ApprovalWorkflowService.executeUserMove()` | Same |
| `ApprovalWorkflowService.executeGroupMemberAdd()` | Same |

---

## Escalation Chain Status

All five chains from the original analysis are **closed**:

| Chain | Findings | Status | What closed it |
|-------|----------|--------|----------------|
| **1** | C1 + C3 | **CLOSED** | Settings endpoint is superadmin-only; playbook `requireApproval` flag is enforced |
| **2** | C2 + H1 | **CLOSED** | Unprovisioned OUs route to directory-level approval; group-member-add has approval gate |
| **3** | H2 + H3 | **CLOSED** | Requester can't edit own payload; `@Version` prevents concurrent approval race |
| **4** | C2 + M4 | **CLOSED** | C2 is fixed; M4 is a documented product decision with feature-key gating |
| **5** | M1 + M2 | **CLOSED** | Audit logs filtered by authorized directories; reviewers filtered by directory profile roles |

---

## Authorization Model Scorecard (Updated)

| Dimension | Original | Current | What changed |
|-----------|----------|---------|--------------|
| **Robustness** | Moderate | **Good** | All critical/high gaps closed; profile-level access enforced in approval execution path |
| **Ease of Application** | Mixed | Mixed | Still three mechanisms (`@RequiresFeature`, `@PreAuthorize`, catch-all) — Wave 4.1 not yet done |
| **Efficiency** | Adequate | Adequate | No caching changes yet — Wave 4.3 not yet done |
| **Consistency** | Poor | **Improved** | All controllers with write endpoints now have explicit authorization; read endpoints still rely on catch-all in some cases |

---

## Remaining Work (Wave 4 — Structural)

The original Wave 4 items are architectural improvements, not security vulnerabilities. None are currently exploitable given the fixes above.

| # | Action | Status | Priority |
|---|--------|--------|----------|
| **4.1** | Standardize on `@RequiresFeature` for all directory-scoped endpoints | **OPEN** | Medium — improves consistency but all endpoints already have some gate |
| **4.2** | Startup-time annotation validator | **OPEN** | Medium — prevents future regressions |
| **4.3** | Request-scoped permission cache | **OPEN** | Low — performance optimization (3N → 3 queries per request) |
| **4.4** | Missing DB index on `admin_profile_roles(admin_account_id)` | **OPEN** | Low — performance |
| **4.5** | Replace over-fetched `findAllByAdminAccountId()` with `existsBy...` | **OPEN** | Low — performance |
| **4.6** | Build-time feature permission matrix | **OPEN** | Low — developer tooling |
| **4.7** | Profile-vs-directory scoping decision | **OPEN** | **High** — the broadest remaining architectural question |

### Wave 4.7 note

The recent `requireProfileAccess` additions are a step toward Option A (DN-level enforcement) but don't complete it. What's enforced now:

- **Profile-scoped write operations**: If a DN matches a profile, the admin must have access to that profile. This covers user create, bulk import, user move, group member add, and approval execution.

What's **not** enforced:

- **Read operations**: `searchUsers()`, `exportUsers()`, directory browsing — an admin in ou=engineering can still search/export ou=sales
- **Operations on DNs outside any profile**: If a DN doesn't match any profile, it goes to directory-level approval (C2 fix), but the DN-scope itself isn't validated against the admin's authorized OUs
- **Playbook target DN scoping**: Playbooks check `requireApproval` (C3 fix) but don't validate the target DN is within the admin's profile OUs

These are the gaps that Wave 4.7 Option A would close. The decision on whether to close them depends on the deployment model (multi-tenant with OU isolation vs. single admin team per directory).

---

## Recommendations

1. **No emergency work remaining.** All critical and high findings are resolved.

2. **Wave 4.7 is the key decision.** If OU-level isolation between admin teams is a requirement, prioritize Option A phases 1-2 (add `requireDnWithinScope` to high-risk read/write paths). If not, document the current model explicitly and move on.

3. **Wave 4.1-4.2 prevent regressions.** Standardizing on `@RequiresFeature` with a startup validator ensures new endpoints can't ship without authorization annotations. Worth doing before adding more controllers.

4. **H4 cleanup is trivial.** The `REPORTS_EXPORT` reference in the original analysis was to a feature key that doesn't exist. No action needed unless a reports export feature is planned.
