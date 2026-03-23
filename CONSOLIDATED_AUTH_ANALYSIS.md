# Consolidated Authorization Architecture Analysis

**Date:** 2026-03-23
**Source:** ADMIN_DIR_PROFILE_CONFLICT.plan (Parts I, II, III)

---

## Executive Summary

The LDAPAdmin authorization system has solid foundations — immutable JWT principals, a well-designed feature override model, and consistent superadmin lockdown — but suffers from **three systemic weaknesses** that compound individual bugs into exploitable escalation paths:

1. **Profile-vs-directory scope collapse** — Admin profile assignments (OU-scoped) are enforced only at the directory level, giving any admin full visibility and operational access across the entire directory regardless of their intended scope.

2. **Inconsistent authorization enforcement** — Three competing mechanisms (SecurityConfig catch-all, `@PreAuthorize`, `@RequiresFeature`) coexist with no enforced convention. 4 of 22 controllers have zero authorization annotations. Read endpoints are systematically unprotected.

3. **Approval workflow gaps** — Multiple bypass paths exist: unprovisioned OUs skip approval entirely, playbook `requireApproval` flags are stored but never checked, and bulk operations skip single-operation approval gates.

These weaknesses interact: an admin can modify global settings (C1) to enable superadmin bypass, target unprovisioned OUs (C2) to skip approval, execute playbooks that ignore their own approval flags (C3), and do all of this across OU boundaries they shouldn't have access to (Part I).

---

## Threat Model: Five Escalation Chains

| Chain | Findings | Attack Path | Impact |
|-------|----------|-------------|--------|
| **1** | C1 + C3 | Admin enables `superadminBypassApproval` via unprotected settings endpoint, then superadmin executes playbooks with `requireApproval=true` — flag is ignored anyway, and bypass setting auto-approves | Full operational control without oversight |
| **2** | C2 + H1 | Admin targets OU with no provisioning profile; approval never triggers for single ops; bulk group-member add also skips approval | Mass group membership changes with zero review |
| **3** | H2 + H3 | Approver tampers with payload while second approver concurrently approves original — race condition allows both to execute | Two LDAP operations, one with tampered data |
| **4** | C2 + M4 | Unprovisioned OUs bypass approval + destructive operations have no approval path | User deletion/disabling with no checks beyond directory feature gate |
| **5** | M1 + M2 | Non-superadmin enumerates all admins via listReviewers + queries audit logs across all directories | Full reconnaissance of admin roster and operational history |

---

## Authorization Model Scorecard

| Dimension | Rating | Root Cause |
|-----------|--------|------------|
| **Robustness** | Moderate | Three parallel auth mechanisms; permissive catch-all; reads unprotected; no startup enforcement |
| **Ease of Application** | Mixed | `@RequiresFeature` is ergonomic but isn't the only mechanism; developers need tribal knowledge |
| **Efficiency** | Adequate | 3 uncached DB queries per `@RequiresFeature` request; missing index; over-fetched base-role query |
| **Consistency** | Poor | 4 controllers with no annotations; 4 with mixed approaches; read/write asymmetry within same controllers |

### Controller Authorization Coverage (22 total)

| Mechanism | Count | Controllers |
|-----------|-------|-------------|
| `@RequiresFeature` only | 6 | AccessReview, BulkGroup, BulkUser, ScheduledReport, User (writes), Group (writes) |
| `@PreAuthorize` only | 8 | 6 superadmin controllers, LifecyclePlaybook, ProvisioningProfile |
| Neither (catch-all only) | 4 | **Approval, CsvMappingTemplate, DirectoryBrowse, Schema** |
| Mixed within controller | 4 | AccessReview, User, Group, ProvisioningProfile |

---

## Consolidated Prioritized Action List

Actions are organized into four waves based on: exploitability, blast radius, fix complexity, and dependency ordering. Each action is tagged with its source finding(s).

### Wave 1: Emergency Fixes (do immediately, ~1 hour total)

These close the worst escalation chains with minimal code changes.

| # | Action | Finding | File(s) | Effort | Closes Chain |
|---|--------|---------|---------|--------|--------------|
| **1.1** | Add `@PreAuthorize("hasRole('SUPERADMIN')")` to `ApplicationSettingsController.get()` and `upsert()` | C1 | `ApplicationSettingsController.java:42` | 5 min | Chain 1 |
| **1.2** | Add `@Version` to `PendingApproval` entity (or pessimistic lock on `findByIdForUpdate`) to prevent double-execution on concurrent approval | H3 | `ApprovalWorkflowService.java:137-189` | 30 min | Chain 3 |
| **1.3** | Block original requester from calling `updatePayload()` on their own pending request; validate payload schema matches original request type; log payload diffs | H2 | `ApprovalWorkflowService.java:227-249` | 30 min | Chain 3 |

### Wave 2: Approval Integrity (next priority, ~4 hours total)

These close the remaining approval bypass paths and enforce the `requireApproval` contract.

| # | Action | Finding | File(s) | Effort | Closes Chain |
|---|--------|---------|---------|--------|--------------|
| **2.1** | Enforce `requireApproval` flag in `LifecyclePlaybookService.execute()` — check `pb.isRequireApproval()`, route to profile approvers or directory-level fallback, add `PLAYBOOK_EXECUTION` request type | C3 | `LifecyclePlaybookService.java:127-189` | 1 hr | Chain 1 |
| **2.2** | Decide policy for operations targeting DNs outside any profile's `targetOuDn`: deny (strict) or route to directory-level default approver. Implement in shared approval-check path. Log when profile resolution fails | C2 | `UserController.java:79-96`, `BulkUserController.java:93-106`, `GroupController.java:142-167` | 2 hr | Chains 2, 4 |
| **2.3** | Extract approval check into shared method; call from both single and bulk group-member endpoints | H1 | `GroupController.java:180-202` | 1 hr | Chain 2 |

### Wave 3: Access Control Hardening (~3 hours total)

These close information leaks, missing gates, and authorization inconsistencies.

| # | Action | Finding | File(s) | Effort |
|---|--------|---------|---------|--------|
| **3.1** | Add `permissionService.requireProfileAccess(principal, profileId)` to `generatePassword` endpoint | H5 | `ProvisioningProfileController.java:123-127` | 10 min |
| **3.2** | Add `@RequiresFeature(REPORTS_EXPORT)` to export endpoint, or remove dead enum value | H4 | `FeatureKey.java:25` | 10 min |
| **3.3** | Replace runtime `isSuperadmin()` check with `@PreAuthorize("hasRole('SUPERADMIN')")` on `AccessReviewController.create()` | M3 | `AccessReviewController.java:60-70` | 5 min |
| **3.4** | Filter audit log queries by admin's authorized directories for non-superadmins | M1 | `AuditLogController.java:47` | 1 hr |
| **3.5** | Filter `listReviewers` to admins with at least one profile role in the given directoryId | M2 | `AccessReviewController.java:42-46` | 30 min |
| **3.6** | Add approval workflow to bulk group import (consistent with BulkUserController), or document intentional omission | M5 | `BulkGroupController.java:63-78` | 1 hr |
| **3.7** | Product decision: which destructive operations (user delete, disable, password reset, group delete, member remove) need approval gates? Document and implement | M4 | `UserController.java:128-186`, `GroupController.java:115-123` | varies |

### Wave 4: Structural — Authorization Architecture Unification (~3-5 days total)

These address the root cause: three competing authorization mechanisms with no enforced convention. Should be planned as a deliberate architectural initiative.

| # | Action | Source | Effort |
|---|--------|--------|--------|
| **4.1** | **Standardize on `@RequiresFeature`** for all directory-scoped endpoints. Add new feature keys: `USER_VIEW`, `GROUP_VIEW` (or `DIRECTORY_BROWSE`), `APPROVAL_MANAGE`, `PLAYBOOK_EXECUTE`, `PLAYBOOK_MANAGE`. Apply to all currently ungated endpoints | Part III, s15a-b | 1-2 days |
| **4.2** | **Add startup-time annotation validator** — `ApplicationListener<ContextRefreshedEvent>` that scans all `@RequestMapping` methods under `/api/v1/directories/**` and fails startup if any lack `@RequiresFeature` | Part III, s15c | 4 hr |
| **4.3** | **Add request-scoped permission cache** (`@RequestScope` bean) — load admin's profile roles and feature overrides once per HTTP request, reuse on subsequent checks. Collapses 3N queries to 3 | Part III, s15d | 4 hr |
| **4.4** | **Add missing DB index** — `CREATE INDEX idx_apr_admin ON admin_profile_roles(admin_account_id)` | Part III, s13f | 15 min |
| **4.5** | **Replace over-fetched query** — change `findAllByAdminAccountId()` in `requireFeature()` to `existsByAdminAccountIdAndBaseRole(adminId, BaseRole.ADMIN)` | Part III, s13g | 30 min |
| **4.6** | **Generate feature permission matrix** at build time from `@RequiresFeature` annotations (annotation processor or test output) | Part III, s15g | 4 hr |
| **4.7** | **Profile-vs-directory scoping decision** — choose Option A (DN-level enforcement), the Alternative (directory-scoped, profiles are provisioning-only), or Hybrid (`enforceProfileScoping` flag per directory). Implement accordingly | Part I, s2-4 | 2-3 days |

---

## Wave 4.7 Decision Matrix: Profile Scoping

This is the largest architectural decision. Summary of trade-offs:

| | Option A: DN-Level Enforcement | Alternative: Directory-Scoped | Hybrid |
|---|---|---|---|
| **Best for** | Multi-tenant, delegated admin, compliance environments | Small-to-medium orgs, single admin team per directory | Mixed deployments |
| **Effort** | ~150-200 LOC, touches 5-6 files | Documentation + cleanup | Both + config surface |
| **Gains** | True OU-level isolation, least-privilege at data level | Simplicity, no DN-scoping edge cases, cross-OU ops just work | Per-directory flexibility |
| **Risks** | Edge cases (service accounts outside OUs, cross-OU transfers) | No OU isolation, may fail compliance audits | Configuration complexity, two code paths to test |

**Recommendation:** If multiple admin teams share directories with different OU responsibilities, go with Option A (phases 1-2 first for highest-risk paths). If one trusted admin team per directory, go with the Alternative and make semantics explicit. The Hybrid flag is viable but adds testing surface.

---

## Effort Summary

| Wave | Total Effort | Cumulative |
|------|-------------|------------|
| Wave 1: Emergency | ~1 hour | 1 hour |
| Wave 2: Approval Integrity | ~4 hours | 5 hours |
| Wave 3: Access Hardening | ~3 hours + product decisions | 8 hours |
| Wave 4: Architecture (excl. 4.7) | ~2-3 days | 3-4 days |
| Wave 4.7: Profile Scoping | ~2-3 days | 5-7 days |

**Waves 1-3 close all 13 specific findings (~8 hours of implementation + testing).**
**Wave 4 eliminates the structural root cause that makes these findings possible in the first place.**
