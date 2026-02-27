package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.entity.enums.SslMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Global application settings (§10.2).
 * Covers branding, session timeout, SMTP mail relay, S3-compatible storage,
 * and admin authentication configuration.  Exactly one singleton row.
 */
@Entity
@Table(name = "application_settings")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    // ── Branding ──────────────────────────────────────────────────────────────

    @Column(name = "app_name", nullable = false)
    private String appName = "LDAP Portal";

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "primary_colour", length = 20)
    private String primaryColour;

    @Column(name = "secondary_colour", length = 20)
    private String secondaryColour;

    // ── Session ───────────────────────────────────────────────────────────────

    @Column(name = "session_timeout_minutes", nullable = false)
    private int sessionTimeoutMinutes = 60;

    // ── SMTP mail relay ───────────────────────────────────────────────────────

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort = 587;

    @Column(name = "smtp_sender_address")
    private String smtpSenderAddress;

    @Column(name = "smtp_username")
    private String smtpUsername;

    /** AES-256 encrypted SMTP password. */
    @Column(name = "smtp_password_encrypted", columnDefinition = "TEXT")
    private String smtpPasswordEncrypted;

    @Column(name = "smtp_use_tls", nullable = false)
    private boolean smtpUseTls = true;

    // ── S3-compatible object storage ──────────────────────────────────────────

    @Column(name = "s3_endpoint_url")
    private String s3EndpointUrl;

    @Column(name = "s3_bucket_name")
    private String s3BucketName;

    @Column(name = "s3_access_key")
    private String s3AccessKey;

    /** AES-256 encrypted S3 secret key. */
    @Column(name = "s3_secret_key_encrypted", columnDefinition = "TEXT")
    private String s3SecretKeyEncrypted;

    @Column(name = "s3_region")
    private String s3Region;

    /** TTL for pre-signed download links in hours (default 24 h per §7.2). */
    @Column(name = "s3_presigned_url_ttl_hours", nullable = false)
    private int s3PresignedUrlTtlHours = 24;

    // ── Admin authentication configuration (V17) ──────────────────────────────

    /**
     * Authentication method governing admin logins.
     * LOCAL = bcrypt password in accounts.password_hash;
     * LDAP = bind against the server described by ldap_auth_* columns.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "admin_auth_type", nullable = false, length = 10)
    private AccountType adminAuthType = AccountType.LOCAL;

    @Column(name = "ldap_auth_host")
    private String ldapAuthHost;

    @Column(name = "ldap_auth_port")
    private Integer ldapAuthPort;

    @Enumerated(EnumType.STRING)
    @Column(name = "ldap_auth_ssl_mode", length = 10)
    private SslMode ldapAuthSslMode;

    @Column(name = "ldap_auth_trust_all_certs", nullable = false)
    private boolean ldapAuthTrustAllCerts = false;

    @Column(name = "ldap_auth_trusted_cert_pem", columnDefinition = "TEXT")
    private String ldapAuthTrustedCertPem;

    /** Optional service-account bind DN for user lookup (may be null). */
    @Column(name = "ldap_auth_bind_dn", length = 500)
    private String ldapAuthBindDn;

    /** AES-256-GCM encrypted service-account bind password. */
    @Column(name = "ldap_auth_bind_password_enc", columnDefinition = "TEXT")
    private String ldapAuthBindPasswordEnc;

    @Column(name = "ldap_auth_user_search_base", length = 500)
    private String ldapAuthUserSearchBase;

    /**
     * Pattern used to construct the user bind DN at authentication time.
     * {@code {username}} is substituted with the supplied username.
     * Example: {@code uid={username},ou=people,dc=example,dc=com}
     */
    @Column(name = "ldap_auth_bind_dn_pattern", length = 500)
    private String ldapAuthBindDnPattern;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
