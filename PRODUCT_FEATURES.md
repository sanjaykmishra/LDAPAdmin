# LDAPAdmin - Product Feature Summary

## Overview

LDAPAdmin is an enterprise-grade web-based LDAP directory administration platform built with Spring Boot 3 (Java 21) and Vue 3. It provides comprehensive user and group lifecycle management, governance and compliance workflows, self-service capabilities, and multi-directory support.

---

## 1. Directory & Connection Management

- **Multi-directory support** - Connect to and manage multiple LDAP servers from a single instance
- **SSL/TLS modes** - NONE, SSL, and STARTTLS connection security options
- **Custom CA certificates** - Import PEM certificates for custom certificate authorities
- **Connection pooling** - Configurable min/max pool sizes with timeout settings
- **Encrypted credential storage** - AES-256 encryption for all stored LDAP bind passwords
- **Paging support** - Configurable page size for large directory queries
- **Realms** - Logical partitions within directories scoped by user/group base DNs, with template linking and per-realm settings

---

## 2. User Management

- **Full CRUD operations** - Create, read, update, and delete user entries
- **Account enable/disable** - Toggle user account status via configurable LDAP attributes
- **User move** - Relocate users between organizational units
- **Password reset** - Admin-initiated password reset with client-side strength validation
- **Bulk user import** - CSV-based import with column-to-attribute mapping, preview mode, and dry-run capability
- **Bulk user export** - CSV export with configurable attribute selection
- **Bulk attribute updates** - Select multiple users and batch-apply attribute changes (set, add, or remove operations)
- **User templates** - Define per-template object classes, required fields, form layouts, and RDN attributes
- **Conflict handling** - Configurable strategies for import conflicts: prompt, skip, or overwrite

---

## 3. Group Management

- **Full CRUD operations** - Create, read, update, and delete group entries
- **Membership management** - Add and remove members from groups individually or in bulk
- **Bulk group operations** - Batch member management and attribute updates
- **Referential integrity checks** - On-demand detection of broken member references, orphaned entries, and empty groups

---

## 4. Directory Browser

- **DIT navigation** - Two-panel Directory Information Tree browser with expandable tree and entry detail view
- **Inline editing** - Edit entry attributes directly from the browser
- **Entry creation** - Create new entries via LDIF-based form
- **LDIF import/export** - Import and export entries in LDIF format with result tracking for successes and failures
- **Advanced search** - Configurable base DN, search scope (BASE, ONE_LEVEL, SUBTREE), LDAP filter, attribute selection, result limits, and saved search filters

---

## 5. Schema Browser

- **Object class listing** - View all object classes with MUST and MAY attribute requirements
- **Attribute type details** - Inspect syntax, single/multi-value flags, and equality matching rules
- **Bulk attribute retrieval** - Retrieve attributes across multiple object classes

---

## 6. Access Reviews (Compliance & Governance)

- **Campaign lifecycle** - Full workflow: create, activate, close, expire, or cancel campaigns
- **Group-scoped reviews** - Scope campaigns to specific groups with per-group reviewer assignment
- **Per-member decisions** - Confirm or revoke access for individual group members with comments
- **Auto-revoke mode** - Automatically remove revoked members or flag for manual action
- **Recurrence scheduling** - Set up recurring review campaigns with configurable deadlines
- **Audit trail** - Complete history of all review decisions for SOX, SOC 2, and ISO 27001 compliance

---

## 7. Approval Workflows

- **Operation-level approvals** - Require approval before executing user creation, group membership changes, or user moves
- **Pending approvals queue** - Centralized view for approvers to review pending operations
- **Approve/reject with comments** - Decision recording with optional justification text
- **Email notifications** - SMTP-based notifications sent to designated approvers
- **Per-realm configuration** - Enable or disable approval requirements per realm and operation type

---

## 8. Role-Based Access Control (RBAC)

### Roles
- **SUPERADMIN** - Full system access across all directories and realms
- **ADMIN** - Realm-scoped administrative access
- **READ_ONLY** - Realm-scoped read-only access

### Feature Permissions (17 keys)
| Category | Permissions |
|----------|------------|
| User operations | create, edit, delete, enable/disable, move, reset password |
| Group operations | edit, manage members, create, delete |
| Bulk operations | import, export, attribute update |
| Reporting | run, export, schedule |
| Access reviews | manage campaigns, review decisions |

- **Enforcement** - AOP-based `@RequiresFeature` annotation and Spring Security `@PreAuthorize` integration

---

## 9. Self-Service Portal

- **Separate login** - End-users authenticate via LDAP bind-as-self, independent from admin login
- **Profile management** - View and edit own LDAP attributes (configurable per template)
- **Password change** - Self-service password change with strength meter
- **Group visibility** - View own group memberships
- **Self-registration** - Public registration with email verification and admin approval workflow
- **Dedicated layout** - Separate UI layout optimized for end-user self-service

---

## 10. Authentication & Security

### Authentication Methods
- **LOCAL** - Username/password stored in the application database (bcrypt hashed)
- **LDAP** - Authenticate against LDAP server with configurable bind DN pattern
- **OIDC** - OpenID Connect SSO via Authorization Code Flow with ID token claims

### Security Features
- **JWT tokens** - Configurable expiry with httpOnly secure cookies
- **AES-256 encryption** - For LDAP passwords, SMTP credentials, S3 keys, and OIDC client secrets
- **Rate limiting** - On login endpoints
- **Mixed auth** - Per-account authentication type (supports different methods for different users)

---

## 11. Audit & Monitoring

- **Internal audit log** - Automatic recording of all LDAP operations with actor, action, target DN, detail JSON, and timestamp
- **External audit sources** - Integration with Oracle DSEE changelog and OpenLDAP accesslog overlay
- **Filtering** - Date range and action type filtering with pagination
- **Pluggable format** - Strategy pattern for different LDAP changelog formats
- **Action types tracked** - USER_CREATE, USER_MODIFY, USER_DELETE, GROUP_CREATE, GROUP_MODIFY, and more

---

## 12. Reporting

- **Scheduled report jobs** - Cron-expression-based scheduling with configurable report types
- **On-demand execution** - Generate reports immediately with CSV download
- **Email delivery** - Send reports to recipients via SMTP
- **S3 export** - Upload reports to S3-compatible storage with presigned URL generation
- **Multiple formats** - CSV output (PDF planned)

---

## 13. User Templates & Form Designer

- **Template system** - Define object classes, required attributes, and form structure per template
- **Visual form layout designer** - Drag-and-drop editor for organizing form fields
- **Section-based organization** - Group fields into logical sections
- **Column spans** - 1 to 3 column layouts per field
- **Input types** - TEXT, TEXTAREA, PASSWORD, BOOLEAN, DATE, DATETIME, MULTI_VALUE, DN_LOOKUP, SELECT, HIDDEN_FIXED
- **Validation** - Required indicators, client-side hints, password strength meter
- **Self-service flags** - Control which fields end-users can edit in the self-service portal

---

## 14. Import/Export & Bulk Operations

- **CSV import/export** - For users and groups with configurable column-to-attribute mapping
- **Reusable mapping templates** - Save and load named CSV mapping configurations
- **LDIF import/export** - Standard LDIF format for directory entries
- **Preview and dry-run** - Validate imports before execution
- **Bulk attribute updates** - Apply changes across multiple entries simultaneously

---

## 15. Administration & Configuration

### Branding & Theming
- Custom application name
- Custom logo URL
- Configurable primary and secondary colors via CSS custom properties

### Application Settings
- SMTP configuration for email delivery
- S3 storage configuration for report exports
- Session timeout settings
- Authentication method enablement (LOCAL, LDAP, OIDC)

### Admin Account Management
- Create and manage admin/superadmin accounts
- Reset admin passwords
- Account activation/deactivation
- Last login tracking

---

## 16. UI/UX Features

- **Responsive web interface** - Vue 3 with Vite for fast development and production builds
- **Toast notifications** - Success, error, and info messages with auto-dismiss
- **Confirmation dialogs** - For destructive actions
- **Copy-to-clipboard** - One-click DN copy with visual feedback
- **Data tables** - Pagination, multi-select checkboxes, and sortable columns
- **DN picker and tree** - Visual DN selection and DIT tree navigation components
- **Reusable form components** - Standardized form fields with validation

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.3.4, Java 21 |
| Frontend | Vue 3, Vite, Vue Router, Pinia |
| Database | PostgreSQL with Flyway migrations |
| LDAP SDK | UnboundID LDAP SDK 7.0.1 |
| Auth | JWT (JJWT 0.12.6), Spring Security |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Logging | Logstash Logback (structured JSON) |
| Build | Maven |

---

## API Surface

22 REST controllers providing endpoints across:

- Authentication & session management
- User and group CRUD with bulk operations
- Approval workflow management
- Access review campaign lifecycle
- Audit log querying
- Scheduled report management
- Schema introspection
- Directory browsing and LDIF operations
- Superadmin directory, realm, and account management

---

*Generated: March 29, 2026*
