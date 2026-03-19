# LDAPAdmin Code Review - Priority Plan

## Priority Ranked Issues

| # | Issue | Impact | Effort | Status |
|---|-------|--------|--------|--------|
| 1 | Add controller tests for 13 untested endpoints | High | High | Pending |
| 2 | Fix CORS default to reject unknown origins | High | Low | Pending |
| 3 | Fix 9 silent error catches in frontend | High | Low | Pending |
| 4 | Unify permission checking (aspect vs manual) | High | Medium | Pending |
| 5 | Fix `TooManyRequestsException` hierarchy | Medium | Low | Pending |
| 6 | Extract duplicate SSL/TLS setup into shared helper | Medium | Low | Pending |
| 7 | Consolidate CSS utilities to `base.css` | Medium | Low | Pending |
| 8 | Add service tests for 8 untested services | High | High | Pending |
| 9 | Fix `setFeaturePermissions` orphaned entries | Medium | Low | Pending |
| 10 | Extract shared frontend composables | Medium | Medium | Pending |
| 11 | Rename `loadRealmAndForms()`, fix stale comments | Low | Low | Pending |
| 12 | Remove unused `counter.js` and `SamlIdpType` enum | Low | Trivial | Pending |
| 13 | Add client-side form validation | Medium | Medium | Pending |
| 14 | Add DN/baseDn validation in realm creation | Medium | Low | Pending |
| 15 | Add HSTS header to security config | Medium | Low | Pending |

## Detailed Descriptions

### 1. Add controller tests for 13 untested endpoints
Controllers lack test coverage. Need integration/unit tests for all REST endpoints.

### 2. Fix CORS default to reject unknown origins
`SecurityConfig` allows `*` if `CORS_ALLOWED_ORIGIN` env var is not set — unsafe for production. Should fail closed.

### 3. Fix 9 silent error catches in frontend
Multiple `catch` blocks silently swallow errors. Users see no feedback when operations fail.

### 4. Unify permission checking (aspect vs manual)
Permission checks scattered across `@PreAuthorize`, `@RequiresFeature` AOP aspect, and manual `PermissionService` calls. `FeaturePermissionAspect` uses fragile reflection-based `directoryId` parameter detection.

### 5. Fix `TooManyRequestsException` hierarchy
Extends `RuntimeException` directly instead of `LdapAdminException` — inconsistent with `GlobalExceptionHandler` patterns.

### 6. Extract duplicate SSL/TLS setup into shared helper
SSL context setup (trust-all, PEM cert, system truststore) duplicated in `AuthenticationService` and `LdapConnectionFactory`/`DirectoryConnectionService`.

### 7. Consolidate CSS utilities to `base.css`
Repeated utility styles across component CSS files. Centralize into `base.css`.

### 8. Add service tests for 8 untested services
Core services (AuthenticationService, PermissionService, DirectoryConnectionService, RealmService, AdminManagementService, AuditService, BulkUserService, EncryptionService) need unit tests.

### 9. Fix `setFeaturePermissions` orphaned entries
`AdminManagementService.setFeaturePermissions()` adds/updates but never deletes old entries — orphaned permissions accumulate.

### 10. Extract shared frontend composables
Repeated patterns across Vue components (loading state, error handling, pagination) should be extracted into shared composables.

### 11. Rename `loadRealmAndForms()`, fix stale comments
Function name doesn't reflect current behavior. Several comments reference outdated logic.

### 12. Remove unused `counter.js` and `SamlIdpType` enum
Dead code: `counter.js` (Vue starter scaffold) and `SamlIdpType` enum (no references).

### 13. Add client-side form validation
Forms rely solely on server-side validation. Add client-side checks for better UX.

### 14. Add DN/baseDn validation in realm creation
No validation that `userBaseDn`/`groupBaseDn` are valid subtrees of the directory's `baseDn`.

### 15. Add HSTS header to security config
`SecurityConfig` does not configure HSTS (HTTP Strict Transport Security) header.

## Additional Notes

- **Backend entities:** `src/main/java/com/ldapadmin/entity/`
- **Backend services:** `src/main/java/com/ldapadmin/service/`
- **Backend controllers:** `src/main/java/com/ldapadmin/controller/`
- **Backend auth:** `src/main/java/com/ldapadmin/auth/`
- **Backend config:** `src/main/java/com/ldapadmin/config/`
- **Frontend views:** `frontend/src/views/`
- **Frontend composables:** `frontend/src/composables/`
