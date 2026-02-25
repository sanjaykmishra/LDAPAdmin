-- V17: Add global admin authentication configuration to application_settings.
--
-- Replaces the per-tenant tenant_auth_configs table (dropped in V13) with a
-- single global auth config stored in the application_settings singleton row.
--
-- admin_auth_type = 'LOCAL'
--   Admin accounts authenticate with a bcrypt password stored in accounts.password_hash.
--   All ldap_auth_* columns are ignored.
--
-- admin_auth_type = 'LDAP'
--   Admin accounts authenticate by performing an LDAP bind against the server
--   described by the ldap_auth_* columns.  The bind DN is constructed from
--   ldap_auth_bind_dn_pattern by substituting {username} with the supplied
--   username at login time (e.g. uid={username},ou=people,dc=example,dc=com).
--   ldap_auth_bind_dn and ldap_auth_bind_password_enc are used by the
--   application to make an initial service-account bind before the user lookup,
--   if the LDAP server requires authentication to search (optional).

ALTER TABLE application_settings

    -- Which authentication method governs admin logins
    ADD COLUMN admin_auth_type             VARCHAR(10)  NOT NULL DEFAULT 'LOCAL',

    -- LDAP server connection details (used when admin_auth_type = 'LDAP')
    ADD COLUMN ldap_auth_host              VARCHAR(255),
    ADD COLUMN ldap_auth_port              INTEGER,
    ADD COLUMN ldap_auth_ssl_mode          VARCHAR(10),
    ADD COLUMN ldap_auth_trust_all_certs   BOOLEAN      NOT NULL DEFAULT FALSE,
    -- PEM-encoded CA certificate for custom trust anchor (used when not trusting all certs)
    ADD COLUMN ldap_auth_trusted_cert_pem  TEXT,

    -- Optional service-account credentials for initial bind / user lookup
    ADD COLUMN ldap_auth_bind_dn           VARCHAR(500),
    -- AES-256-GCM encrypted bind password; decrypted by EncryptionService at runtime
    ADD COLUMN ldap_auth_bind_password_enc TEXT,

    -- Base DN under which user entries are searched
    ADD COLUMN ldap_auth_user_search_base  VARCHAR(500),
    -- Pattern used to construct the user bind DN; {username} is substituted at
    -- authentication time.  Example: uid={username},ou=people,dc=example,dc=com
    ADD COLUMN ldap_auth_bind_dn_pattern   VARCHAR(500),

    ADD CONSTRAINT chk_admin_auth_type
        CHECK (admin_auth_type IN ('LOCAL', 'LDAP')),
    ADD CONSTRAINT chk_ldap_auth_ssl_mode
        CHECK (ldap_auth_ssl_mode IS NULL OR ldap_auth_ssl_mode IN ('NONE', 'LDAPS', 'STARTTLS'));
