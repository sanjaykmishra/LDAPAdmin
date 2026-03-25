# Active Directory Integration Analysis

## Current State

LDAPAdmin has solid generic LDAP support that works with OpenLDAP, 389DS, and the majority of AD operations (search, group management, user creation). However, it is **not production-ready for Active Directory** due to critical gaps in password reset functionality and changelog support. The codebase is architecturally sound and well-positioned to add AD-specific features — most gaps are implementation details rather than fundamental design flaws.

### What already works with AD (no changes needed)

- Basic bind authentication (simple bind with DN or UPN)
- User/group search with pagination (handles AD's 1000-entry MaxPageSize)
- `sAMAccountName` and `memberOf` accessors in `LdapUser.java`
- Schema discovery via `LdapSchemaService.java`
- SSL/STARTTLS connections
- Enable/disable users (configurable attribute mapping)
- Integrity checks (generic, works on any directory)
- Directory tree browsing
- User CRUD (create, read, update, delete)
- Move/rename (ModifyDN) operations

---

## Gaps and Required Changes

### 1. Password Resets (Critical — blocks AD deployments)

**Problem:** `LdapUserService.java:315-319` uses the `userPassword` attribute. Active Directory uses the `unicodePwd` attribute with specific encoding requirements.

**AD Requirements:**
- Attribute: `unicodePwd` (not `userPassword`)
- Encoding: password wrapped in double quotes, then encoded as UTF-16LE bytes
- Transport: SSL/TLS connection required for password operations
- Example encoding: `("newPassword").getBytes("UTF-16LE")`

**Files to modify:**
- `src/main/java/com/ldapadmin/ldap/LdapUserService.java` — `resetPassword()` method
- `src/main/java/com/ldapadmin/entity/DirectoryConnection.java` — add directory type flag

---

### 2. Nested Group Resolution (Critical — incomplete access reviews)

**Problem:** `LdapGroupService.java` only resolves direct group memberships. AD heavily uses nested groups, so access review campaigns would miss indirect members.

**AD Solution:**
- Use `LDAP_MATCHING_RULE_IN_CHAIN` (OID `1.2.840.113556.1.4.1941`) in search filters
- Example filter: `(memberOf:1.2.840.113556.1.4.1941:=CN=GroupName,DC=corp,DC=local)`
- Fallback: recursive client-side group enumeration for non-AD directories

**Files to modify:**
- `src/main/java/com/ldapadmin/ldap/LdapGroupService.java` — add `getNestedMembers()` method

---

### 3. AD Changelog / DirSync Support (High — needed for audit/compliance)

**Problem:** `ChangelogFormat` enum only has `DSEE_CHANGELOG` and `OPENLDAP_ACCESSLOG`. Neither works with AD. Changes made directly in AD outside of LDAPAdmin are not captured.

**AD Solution:**
- Implement DirSync control (OID `1.2.840.113556.1.4.417`) for polling AD changes
- DirSync returns incremental changes since last cookie, similar to a changelog

**Files to modify/add:**
- `src/main/java/com/ldapadmin/entity/enums/ChangelogFormat.java` — add `AD_DIRSYNC`
- `src/main/java/com/ldapadmin/ldap/changelog/DirSyncChangelogStrategy.java` — new strategy class
- `src/main/java/com/ldapadmin/ldap/LdapChangelogReader.java` — integrate new strategy

---

### 4. userAccountControl Decoding (Medium — poor admin experience without it)

**Problem:** `LdapUser.java:54-57` exposes the raw `userAccountControl` integer. The UI cannot show human-readable status like "disabled", "locked out", "password expired", etc.

**AD `userAccountControl` flags:**
| Flag | Hex | Meaning |
|------|-----|---------|
| ACCOUNTDISABLE | 0x0002 | Account is disabled |
| LOCKOUT | 0x0010 | Account is locked out |
| PASSWD_NOTREQD | 0x0020 | No password required |
| NORMAL_ACCOUNT | 0x0200 | Default account type |
| DONT_EXPIRE_PASSWD | 0x10000 | Password never expires |
| PASSWORD_EXPIRED | 0x800000 | Password has expired |

**Files to add/modify:**
- `src/main/java/com/ldapadmin/util/UserAccountControlDecoder.java` — new utility class
- Frontend components displaying user status — decode and render flags

---

### 5. Multi-DC Failover and Global Catalog (Medium — enterprise resilience)

**Problem:** `LdapConnectionFactory.java:188` uses `SingleServerSet` only. No support for multiple domain controllers or Global Catalog queries.

**AD Solution:**
- Use UnboundID's `FailoverServerSet` or `RoundRobinServerSet` for multiple DCs
- Add optional Global Catalog port (3268/3269) configuration for forest-wide searches
- Add health check / automatic failover between domain controllers

**Files to modify:**
- `src/main/java/com/ldapadmin/ldap/LdapConnectionFactory.java` — connection set selection
- `src/main/java/com/ldapadmin/entity/DirectoryConnection.java` — add additional host/port fields

---

### 6. Directory Type Presets in UI (Medium — onboarding friction)

**Problem:** `DirectoriesManageView.vue` has no "Active Directory" selector. Admins must manually configure AD-specific values for object classes, RDN attribute, login attribute, etc.

**AD Presets to auto-populate:**
- Object classes: `user`, `person`, `organizationalPerson`, `top`
- RDN attribute: `cn`
- Login attribute: `sAMAccountName`
- User search filter: `(objectClass=user)`
- Group search filter: `(objectClass=group)`
- Member attribute: `member`
- Default user container: `CN=Users`

**Files to modify:**
- `frontend/src/views/superadmin/DirectoriesManageView.vue` — add directory type dropdown with presets

---

## Additional Considerations

### SASL / Kerberos Authentication

- Current: simple bind only
- AD service accounts typically use simple bind, so this is not a blocker
- GSSAPI/Kerberos support would be needed for environments that disable simple binds
- Priority: Low (most AD deployments allow simple bind for service accounts)

### UPN-Style Bind Format

- Code accepts any `bindDn` value; UPN format (`user@domain.com`) is supported by AD natively
- No code changes needed, but could add validation/guidance in UI
- Priority: Low

### AD-Specific Integrity Checks

- Could add checks for: orphaned computer accounts, stale disabled accounts, accounts with non-expiring passwords
- Priority: Low (nice-to-have, not blocking)

---

## Recommended Implementation Order

| Priority | Item | Effort Estimate | Impact |
|----------|------|----------------|--------|
| 1 | Password reset (`unicodePwd`) | Small | Unblocks core AD workflow |
| 2 | Nested group resolution | Medium | Completes access review campaigns |
| 3 | DirSync changelog strategy | Medium | Enables AD audit/compliance |
| 4 | `userAccountControl` decoder | Small | Better admin experience |
| 5 | Directory type presets in UI | Small | Smoother AD onboarding |
| 6 | Multi-DC failover / Global Catalog | Medium | Enterprise resilience |

---

## Key File References

### Core LDAP Layer
- `src/main/java/com/ldapadmin/ldap/LdapConnectionFactory.java` — Connection pooling & SSL
- `src/main/java/com/ldapadmin/ldap/LdapUserService.java` — User CRUD, password reset
- `src/main/java/com/ldapadmin/ldap/LdapGroupService.java` — Group CRUD & membership
- `src/main/java/com/ldapadmin/ldap/LdapSchemaService.java` — Schema discovery
- `src/main/java/com/ldapadmin/ldap/IntegrityCheckService.java` — Referential integrity
- `src/main/java/com/ldapadmin/ldap/LdapChangelogReader.java` — Scheduled changelog polling

### Changelog Strategies
- `src/main/java/com/ldapadmin/ldap/changelog/ChangelogStrategy.java` — Interface
- `src/main/java/com/ldapadmin/ldap/changelog/DseeChangelogStrategy.java` — Oracle/UnboundID
- `src/main/java/com/ldapadmin/ldap/changelog/AccesslogStrategy.java` — OpenLDAP

### Models
- `src/main/java/com/ldapadmin/ldap/model/LdapUser.java` — User projection (has AD accessors)
- `src/main/java/com/ldapadmin/ldap/model/LdapGroup.java` — Group projection
- `src/main/java/com/ldapadmin/ldap/LdapEntryMapper.java` — Entry mapping

### Configuration
- `src/main/java/com/ldapadmin/entity/DirectoryConnection.java` — Directory config
- `src/main/java/com/ldapadmin/entity/AuditDataSource.java` — Audit source config
- `src/main/java/com/ldapadmin/entity/enums/ChangelogFormat.java` — Changelog format enum

### Frontend
- `frontend/src/views/superadmin/DirectoriesManageView.vue` — Directory CRUD UI
