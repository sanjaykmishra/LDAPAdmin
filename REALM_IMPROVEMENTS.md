# Realms & User Templates — Design Analysis and Improvement Suggestions

## Current Design Summary

**Realms** are logical partitions within a directory connection, each defining a user base DN, group base DN, and linked user templates. They serve as the primary permission boundary — admins get roles (ADMIN/READ_ONLY) per realm. Realms also hold key-value settings for approval workflows and approver configuration (dual-mode: LDAP group or database table).

**User Templates** define how LDAP attributes are presented in user create/edit forms — input types, required fields, layout sections, column spans, RDN designation, and display order. Templates are linked to realms via a `RealmObjectclass` junction table and associated with specific LDAP objectClasses. A visual drag-and-drop layout designer (`FormLayoutDesigner.vue`) lets superadmins organize fields into sections.

---

## Realm Design Improvements

### 1. Realm Hierarchy / Inheritance
Currently realms are flat. Supporting parent-child relationships would let organizations model `Company > Division > Department` where child realms inherit settings and templates from parents but can override specific values. This avoids duplicating configuration across dozens of similar realms.

### 2. Realm-Scoped Feature Permissions
Feature permissions (`FeatureKey`) are currently per-admin, not per-realm. An admin with `USER_CREATE` can create users in every realm they have access to. Consider making feature keys realm-scoped so an admin could have `USER_CREATE` in the Employees realm but only `USER_VIEW` in the Contractors realm.

### 3. Default Realm per Directory
There's no concept of a default realm. When a user or group falls outside all configured realm base DNs, the UI has no template to apply. A fallback/default realm per directory would handle edge cases gracefully.

### 4. Realm-Level Policies Beyond Approvals
The `RealmSetting` key-value store is only used for approval workflow settings today. Extend it for:
- Password policy overrides (min length, complexity per realm)
- Account expiration defaults (e.g., contractor accounts expire after 90 days)
- Required group memberships on creation (auto-add to baseline groups)
- Self-service feature toggles per realm (which realms allow self-registration)

### 5. Realm Overlap Detection
Nothing prevents two realms in the same directory from having overlapping base DNs (e.g., `ou=people,dc=corp` and `ou=engineering,ou=people,dc=corp`). A user under `ou=engineering` would match both. Add validation to warn about or prevent overlapping base DNs, or define explicit precedence rules.

### 6. Realm Dashboard / Statistics
Each realm could show summary metrics: user count, group count, pending approvals, active access review campaigns, last audit event. This gives admins immediate context when switching between realms.

---

## User Template Design Improvements

### 7. Template Versioning
Editing a template takes effect immediately for all realms using it. There's no history, no rollback, no way to preview changes before they go live. Add template versioning so admins can draft changes, preview the resulting form, and publish when ready.

### 8. Attribute Default Values & Auto-Generation
Templates define which attributes appear and how they're validated, but there's no support for default values or auto-generated values. Common needs:
- Default `loginShell` to `/bin/bash`
- Auto-generate `uidNumber` from a sequence
- Compute `cn` from `${givenName} ${sn}`
- Set `homeDirectory` to `/home/${uid}`

A simple expression language or formula field would handle most cases.

### 9. Attribute Validation Rules
The `InputType` enum controls the widget but there's no regex validation, min/max length, allowed values list, or conditional visibility. Adding these would catch errors before they reach the LDAP server:
- Regex pattern (e.g., `^[a-z][a-z0-9-]{2,15}$` for uid)
- Allowed values dropdown (e.g., department list)
- Min/max length
- Conditional visibility (show field X only when field Y = "contractor")

### 10. Group Templates
User templates exist but there's no equivalent for groups. Group creation currently uses hardcoded fields. A `GroupTemplate` would let admins define which attributes appear when creating groups, set defaults for `groupOfNames` vs `posixGroup` vs `groupOfUniqueNames`, and control the member attribute type.

### 11. Template Cloning
No ability to duplicate a template. Admins managing many similar realms (e.g., regional offices with slightly different fields) would benefit from cloning an existing template and tweaking the differences.

### 12. Self-Service Attribute Designation
The `SELF_SERVICE_PLAN.md` mentions marking attributes as `selfServiceEditable`, but this isn't implemented in `UserTemplateAttributeConfig`. Add a `selfServiceEditable` boolean so templates can control which fields end-users can modify in the self-service portal versus which are admin-only.

### 13. Multi-Value Attribute Handling
The `MULTI_VALUE` input type exists but there's limited UI support for managing ordered lists, adding/removing values, or setting a primary value. Improve the multi-value editor with proper add/remove buttons, reordering, and max-count enforcement.

### 14. Template Preview Mode
The layout designer shows a visual representation, but there's no way to preview the actual rendered form with sample data. A "Preview as User Form" button that shows exactly what an admin would see when creating a user would prevent trial-and-error configuration.

---

## Architecture & Integration Improvements

### 15. Realm-Template Binding Simplification
The `RealmObjectclass` junction table adds a layer of indirection (`objectClassId` UUID) that's not fully utilized — it's often null or opaque. Consider simplifying to a direct `realm_user_templates` many-to-many table, since templates already carry their own `objectClassNames`.

### 16. Template-Driven Self-Registration
The `RegisterView.vue` uses hardcoded fields (`givenName`, `sn`, `uid`, `mail`, `telephoneNumber`). It should dynamically render fields based on the template linked to the selected realm, respecting the same layout, validation, and visibility rules. This would make registration forms consistent with admin-created users.

### 17. Import/Export Templates
No way to export a template as JSON and import it into another LDAPAdmin instance. Useful for organizations managing multiple deployments or sharing best-practice templates.

### 18. Audit Trail for Template & Realm Changes
Template and realm modifications aren't tracked in the audit log. Since these configurations affect what admins can do and see, changes should be audited: who modified the template, what changed, when.

---

## Quick Wins

- **Realm count badge** in the sidebar showing how many realms each directory has
- **Template usage indicator** showing which realms reference each template (prevents accidental deletion of in-use templates)
- **Drag-and-drop attribute reordering** in the template editor (partially exists via layout designer, but the attributes tab lacks it)
- **Bulk realm creation** for organizations with many OUs that map 1:1 to realms
- **Template diff view** when updating — show what changed before saving
