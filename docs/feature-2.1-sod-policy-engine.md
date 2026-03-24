# Feature 2.1: Separation of Duties (SoD) Policy Engine

## Overview

Define mutually exclusive group pairs (e.g., "Finance Admin" and "Audit Committee") and enforce separation of duties. When a user is added to a group that conflicts with another group they already belong to, the system either blocks the operation (BLOCK) or creates a violation record and alerts (ALERT).

## What Was Built

### Backend

| Component | File |
|---|---|
| V40 migration (tables) | `src/main/resources/db/migration/V40__sod_policies.sql` |
| V41 migration (permissions) | `src/main/resources/db/migration/V41__sod_feature_permissions.sql` |
| SodPolicy entity | `src/main/java/com/ldapadmin/entity/SodPolicy.java` |
| SodViolation entity | `src/main/java/com/ldapadmin/entity/SodViolation.java` |
| Enums | `SodSeverity.java`, `SodAction.java`, `SodViolationStatus.java` |
| Repositories | `SodPolicyRepository.java`, `SodViolationRepository.java` |
| DTOs | `CreateSodPolicyRequest`, `UpdateSodPolicyRequest`, `SodPolicyResponse`, `SodViolationResponse`, `SodScanResultDto`, `ExemptViolationRequest` |
| Service | `SodPolicyService.java` |
| Controller | `SodPolicyController.java` |
| Exception | `SodViolationException.java` |
| Feature keys | `SOD_MANAGE`, `SOD_VIEW` added to `FeatureKey.java` |
| Audit actions | `SOD_POLICY_CREATED/UPDATED/DELETED`, `SOD_SCAN_EXECUTED`, `SOD_VIOLATION_DETECTED/EXEMPTED/BLOCKED` |
| Integration | SoD check in `LdapOperationService.addGroupMember()` |

### Frontend

| Component | File |
|---|---|
| API layer | `frontend/src/api/sodPolicies.js` |
| Pinia store | `frontend/src/stores/sodPolicies.js` |
| Policy list view | `frontend/src/views/sodPolicies/SodPoliciesView.vue` |
| Policy create/edit form | `frontend/src/views/sodPolicies/SodPolicyFormView.vue` |
| Violations view | `frontend/src/views/sodPolicies/SodViolationsView.vue` |
| Router entries | `frontend/src/router/index.js` |

## API Endpoints

| Method | Path | Description | Permission |
|---|---|---|---|
| `POST` | `/api/v1/directories/{dirId}/sod-policies` | Create policy | `sod.manage` |
| `GET` | `/api/v1/directories/{dirId}/sod-policies` | List policies | `sod.view` |
| `GET` | `/api/v1/directories/{dirId}/sod-policies/{id}` | Get policy | `sod.view` |
| `PUT` | `/api/v1/directories/{dirId}/sod-policies/{id}` | Update policy | `sod.manage` |
| `DELETE` | `/api/v1/directories/{dirId}/sod-policies/{id}` | Delete policy | `sod.manage` |
| `POST` | `/api/v1/directories/{dirId}/sod-policies/scan` | Trigger full scan | `sod.manage` |
| `GET` | `/api/v1/directories/{dirId}/sod-policies/violations` | List violations | `sod.view` |
| `POST` | `/api/v1/directories/{dirId}/sod-policies/violations/{id}/exempt` | Exempt violation | `sod.manage` |

## Frontend Routes

| Path | View |
|---|---|
| `/directories/:dirId/sod-policies` | Policy list with violation counts and scan button |
| `/directories/:dirId/sod-policies/new` | Create policy form with group picker |
| `/directories/:dirId/sod-policies/:policyId/edit` | Edit policy form |
| `/directories/:dirId/sod-violations` | Violation list with status filter and exempt action |

## Manual Testing Guide

### Prerequisites

1. Running LDAPAdmin instance with at least one LDAP directory connected
2. Two or more LDAP groups with some overlapping members
3. Admin account with `sod.manage` permission (SUPERADMINs have all permissions)

### Test 1: Create a SoD Policy

1. Navigate to `/directories/{dirId}/sod-policies`
2. Click "New Policy"
3. Fill in:
   - Name: "Finance vs Audit"
   - Description: "Finance admins cannot also be auditors"
   - Group A DN: DN of first LDAP group
   - Group B DN: DN of second LDAP group
   - Severity: HIGH
   - Action: ALERT
   - Enabled: checked
4. Click "Create Policy"
5. **Expected**: Policy appears in the list

### Test 2: Run a Directory Scan

1. Ensure at least one user exists in both groups defined in the policy
2. On the SoD Policies list page, click "Run Scan"
3. **Expected**: Scan result banner shows violations found
4. Policy card shows non-zero violation count
5. Click "View all violations" link
6. **Expected**: Violations listed with OPEN status, showing user DN and policy name

### Test 3: BLOCK Policy Enforcement

1. Create a policy with Action = BLOCK
2. Try to add a user to Group A who is already a member of Group B
   - Use the Groups page > select Group A > add member
3. **Expected**: 409 Conflict error with message "SoD policy 'X' violated: user 'Y' cannot be in both 'A' and 'B'"
4. User is NOT added to the group
5. Check audit log — `sod.violation_blocked` event should be recorded

### Test 4: ALERT Policy Enforcement

1. Create a policy with Action = ALERT
2. Add a user to Group A who is already in Group B
3. **Expected**: Operation succeeds (user is added)
4. A new OPEN violation appears in the violations list
5. `sod.violation_detected` audit event is recorded with action=ALERT

### Test 5: Exempt a Violation

1. Navigate to the violations list
2. Find an OPEN violation and click "Exempt"
3. Enter a business justification reason
4. Click "Confirm Exemption"
5. **Expected**: Violation status changes to EXEMPTED
6. `sod.violation_exempted` audit event is recorded

### Test 6: Auto-Resolution via Scan

1. Have an OPEN violation for a user in both groups
2. Remove the user from one of the groups in LDAP
3. Run a new scan via "Run Scan" button
4. **Expected**: Violation status changes to RESOLVED

### Test 7: Edit and Delete Policy

1. Click on a policy in the list
2. Change the severity from HIGH to MEDIUM, click "Update Policy"
3. **Expected**: Policy updated successfully
4. Click "Delete Policy" and confirm
5. **Expected**: Policy and its violations are removed

### Test 8: Disabled Policy

1. Create a policy and uncheck "Enabled"
2. Run a scan
3. **Expected**: Disabled policy is skipped during scan
4. Adding a conflicting member is allowed (disabled policies are not enforced)

## Unit Tests

```bash
mvn test -Dtest=SodPolicyServiceTest
```

17 test cases covering:
- CRUD operations (create, update, delete, list)
- Directory scan (new violations, resolution, duplicate skip)
- Real-time membership check (BLOCK throws, ALERT creates violation, no conflict passes)
- Unrelated group ignored, missing directory handled gracefully
- Violation management (exempt, resolve, list with/without filter)
