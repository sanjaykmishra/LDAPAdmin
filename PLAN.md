# Plan: Extend Changelog Poller to Support OpenLDAP accesslog Overlay

## Background

The current `LdapChangelogReader` only supports the **Oracle/Sun DSEE `cn=changelog`** format:
- Entries use `objectClass=changeLogEntry`
- Keyed by integer `changeNumber`
- Attributes: `targetDN`, `changeType`, `changes`, `newRDN`, `deleteOldRDN`, `newSuperior`, `changeTime`, `creatorsName`

OpenLDAP uses the **`slapo-accesslog` overlay** which is fundamentally different:
- Entries live under a configurable suffix (typically `cn=accesslog`)
- Keyed by `reqStart` (GeneralizedTime timestamp, e.g. `20260319143022.000006Z#000001#000#000000`)
- Object classes: `auditModify`, `auditAdd`, `auditDelete`, `auditModRDN`
- Attributes: `reqDN`, `reqType`, `reqStart`, `reqEnd`, `reqResult`, `reqMod`, `reqOld`, `reqNewRDN`, `reqNewSuperior`, `reqDeleteOldRDN`, `reqAuthzID`
- `reqResult=0` means success; non-zero means the operation failed

## Design: Strategy Pattern

Introduce a `ChangelogStrategy` interface with two implementations:
1. `DseeChangelogStrategy` — existing `cn=changelog` logic (extract from `LdapChangelogReader`)
2. `AccesslogStrategy` — new OpenLDAP `cn=accesslog` logic

`AuditDataSource` gets a `changelogFormat` enum field (`DSEE_CHANGELOG` or `OPENLDAP_ACCESSLOG`) that tells the poller which strategy to use. Defaults to `DSEE_CHANGELOG` for backward compatibility.

No changes to `AuditEvent`, `AuditService`, `AuditEventRepository`, or the audit log UI — the existing `changelogChangeNumber` (VARCHAR 255) and JSONB `detail` column accommodate both formats.

---

## Steps

### Step 1: `ChangelogFormat` enum

**New file:** `src/main/java/com/ldapadmin/entity/enums/ChangelogFormat.java`

```java
public enum ChangelogFormat {
    DSEE_CHANGELOG,      // Oracle DSEE / UnboundID — cn=changelog with changeNumber
    OPENLDAP_ACCESSLOG   // OpenLDAP slapo-accesslog — cn=accesslog with reqStart
}
```

### Step 2: Migration — add `changelog_format` column

**New file:** `src/main/resources/db/migration/V14__accesslog_support.sql`

```sql
ALTER TABLE audit_data_sources
    ADD COLUMN changelog_format VARCHAR(25) NOT NULL DEFAULT 'DSEE_CHANGELOG';

ALTER TABLE audit_data_sources
    ADD CONSTRAINT chk_changelog_format
    CHECK (changelog_format IN ('DSEE_CHANGELOG', 'OPENLDAP_ACCESSLOG'));
```

Existing rows get `DSEE_CHANGELOG` via the DEFAULT — fully backward compatible.

### Step 3: Add field to `AuditDataSource` entity

Add to `AuditDataSource.java`:
```java
@Enumerated(EnumType.STRING)
@Column(name = "changelog_format", nullable = false, length = 25)
private ChangelogFormat changelogFormat = ChangelogFormat.DSEE_CHANGELOG;
```

### Step 4: `ChangelogStrategy` interface

**New file:** `src/main/java/com/ldapadmin/ldap/changelog/ChangelogStrategy.java`

```java
public interface ChangelogStrategy {
    /** Build the LDAP search request for this changelog format. */
    SearchRequest buildSearchRequest(AuditDataSource src, int sizeLimit);

    /** Extract a unique entry identifier (changeNumber or reqStart). Null → skip. */
    String extractEntryId(SearchResultEntry entry);

    /** Extract the target DN of the changed entry. */
    String extractTargetDn(SearchResultEntry entry);

    /** Build the detail map for the AuditEvent. */
    Map<String, Object> extractDetail(SearchResultEntry entry);

    /** Extract the timestamp when the operation occurred. */
    OffsetDateTime extractOccurredAt(SearchResultEntry entry);

    /** Whether this entry represents a recordable operation. */
    boolean isRecordable(SearchResultEntry entry);
}
```

### Step 5: `DseeChangelogStrategy`

**New file:** `src/main/java/com/ldapadmin/ldap/changelog/DseeChangelogStrategy.java`

Extract existing logic from `LdapChangelogReader`:
- Filter: `(objectClass=changeLogEntry)` (with optional `targetDN` branch filter)
- Attributes: `changeNumber`, `changeType`, `targetDN`, `changes`, `newRDN`, `deleteOldRDN`, `newSuperior`, `changeTime`, `creatorsName`
- Entry ID: `changeNumber`
- Target DN: `targetDN`
- Timestamp: `changeTime` (GeneralizedTime)
- `isRecordable()`: always true (cn=changelog only contains completed writes)

### Step 6: `AccesslogStrategy`

**New file:** `src/main/java/com/ldapadmin/ldap/changelog/AccesslogStrategy.java`

- Filter: `(&(objectClass=auditWriteObject)(reqResult=0))` — only successful write ops
  - `auditWriteObject` is the parent objectClass of `auditAdd`, `auditModify`, `auditDelete`, `auditModRDN`
  - With optional branch filter: `(&(objectClass=auditWriteObject)(reqResult=0)(reqDN=*<branchFilterDn>))`
- Attributes: `reqStart`, `reqType`, `reqDN`, `reqResult`, `reqMod`, `reqOld`, `reqNewRDN`, `reqNewSuperior`, `reqDeleteOldRDN`, `reqAuthzID`
- Entry ID: `reqStart` (e.g. `20260319143022.000006Z#000001#000#000000`)
- Target DN: `reqDN`
- Timestamp: parse `reqStart` timestamp portion as GeneralizedTime
- Detail map:
  - `changeType`: mapped from `reqType` (`add`→`add`, `modify`→`modify`, `delete`→`delete`, `modrdn`→`modrdn`)
  - `changes`: joined `reqMod` values (multi-valued; each value like `replace: mail\nmail: new@example.com\n-`)
  - `creatorsName`: extracted from `reqAuthzID` (strip `dn:` prefix)
  - For modrdn: `newRDN`, `deleteOldRDN`, `newSuperior` from `reqNewRDN`, `reqDeleteOldRDN`, `reqNewSuperior`
- `isRecordable()`: always true (search filter already handles filtering)

### Step 7: Refactor `LdapChangelogReader`

1. Add strategy factory:
   ```java
   private ChangelogStrategy strategyFor(AuditDataSource src) {
       return switch (src.getChangelogFormat()) {
           case DSEE_CHANGELOG     -> new DseeChangelogStrategy();
           case OPENLDAP_ACCESSLOG -> new AccesslogStrategy();
       };
   }
   ```

2. Refactor `pollSource()`:
   ```java
   private void pollSource(AuditDataSource src) {
       ChangelogStrategy strategy = strategyFor(src);
       List<DirectoryConnection> linkedDirs = resolveLinkedDirs(src);
       try (LDAPConnection conn = openConnection(src)) {
           SearchRequest searchReq = strategy.buildSearchRequest(src, MAX_CHANGELOG_ENTRIES_PER_POLL);
           SearchResult result = conn.search(searchReq);
           for (SearchResultEntry entry : result.getSearchEntries()) {
               processEntry(src, linkedDirs, entry, strategy);
           }
       }
   }
   ```

3. Refactor `processEntry()` to delegate to the strategy for all attribute extraction.

4. Move `parseGeneralizedTime()` to a shared utility used by both strategies.

### Step 8: Update DTOs

**`AuditSourceRequest`**: Add `@NotNull ChangelogFormat changelogFormat` with default.

**`AuditSourceResponse`**: Add `ChangelogFormat changelogFormat`.

**`AuditDataSourceService`**: Map the field in `create()`, `update()`, and response DTO construction.

### Step 9: Update `testConnection()`

`AuditDataSourceService.testConnection()` should use the correct strategy's search filter when verifying the changelog base DN is accessible. Build the search request via the strategy with `sizeLimit=1`.

### Step 10: Update frontend

In `AuditSourcesView.vue`:
- Add a "Changelog Format" select field with options:
  - `DSEE_CHANGELOG` — "Oracle DSEE / cn=changelog"
  - `OPENLDAP_ACCESSLOG` — "OpenLDAP accesslog"
- When user selects `OPENLDAP_ACCESSLOG`, auto-change `changelogBaseDn` default from `cn=changelog` to `cn=accesslog` (only if the current value equals `cn=changelog`)
- Include `changelogFormat` in create/update payloads and display it in the source list

### Step 11: Tests

**`DseeChangelogStrategyTest`**: Verify search filter, attribute extraction, timestamp parsing with mock `SearchResultEntry` objects.

**`AccesslogStrategyTest`**: Verify:
- Search filter includes `(reqResult=0)` and `auditWriteObject`
- `reqStart` parsed as entry ID and timestamp
- `reqDN` → target DN
- `reqMod` multi-value → detail `changes`
- `reqAuthzID` → `creatorsName` (strip `dn:` prefix)
- `reqType` → `changeType` mapping
- modrdn entries: `reqNewRDN`, `reqDeleteOldRDN`, `reqNewSuperior`

---

## Files Summary

| File | Change |
|------|--------|
| `entity/enums/ChangelogFormat.java` | **New** — enum |
| `db/migration/V14__accesslog_support.sql` | **New** — migration |
| `entity/AuditDataSource.java` | Add `changelogFormat` field |
| `ldap/changelog/ChangelogStrategy.java` | **New** — interface |
| `ldap/changelog/DseeChangelogStrategy.java` | **New** — extracted from LdapChangelogReader |
| `ldap/changelog/AccesslogStrategy.java` | **New** — OpenLDAP implementation |
| `ldap/LdapChangelogReader.java` | Refactor to delegate to strategy |
| `dto/audit/AuditSourceRequest.java` | Add `changelogFormat` |
| `dto/audit/AuditSourceResponse.java` | Add `changelogFormat` |
| `service/AuditDataSourceService.java` | Map new field |
| `views/superadmin/AuditSourcesView.vue` | Add format selector |
| `test/.../DseeChangelogStrategyTest.java` | **New** |
| `test/.../AccesslogStrategyTest.java` | **New** |

## What is NOT changing

- **`AuditEvent` entity / `audit_events` table** — `changelogChangeNumber` (VARCHAR 255) fits both `changeNumber` and `reqStart`; JSONB `detail` is schema-free
- **`AuditService`** — already accepts generic string ID and detail map
- **`AuditEventRepository`** — `existsByDirectoryIdAndChangelogChangeNumber` works for both (string comparison)
- **Audit log UI / query API** — events from both formats appear identically
