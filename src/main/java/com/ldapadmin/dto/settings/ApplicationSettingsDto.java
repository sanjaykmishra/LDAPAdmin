package com.ldapadmin.dto.settings;

import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.entity.enums.SiemFormat;
import com.ldapadmin.entity.enums.SiemProtocol;
import com.ldapadmin.entity.enums.SslMode;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Read DTO for global application settings.
 * Encrypted credential fields are never returned; instead boolean flags
 * indicate whether a password/secret has been configured.
 */
public record ApplicationSettingsDto(
        UUID id,

        // Branding
        String appName,
        String logoUrl,
        String primaryColour,
        String secondaryColour,

        // Approval workflow
        boolean superadminBypassApproval,

        // Session
        int sessionTimeoutMinutes,

        // SMTP
        String smtpHost,
        Integer smtpPort,
        String smtpSenderAddress,
        String smtpUsername,
        boolean smtpPasswordConfigured,
        boolean smtpUseTls,

        // S3
        String s3EndpointUrl,
        String s3BucketName,
        String s3AccessKey,
        boolean s3SecretKeyConfigured,
        String s3Region,
        int s3PresignedUrlTtlHours,

        // Authentication
        Set<AccountType> enabledAuthTypes,

        // LDAP auth provider
        String ldapAuthHost,
        Integer ldapAuthPort,
        SslMode ldapAuthSslMode,
        boolean ldapAuthTrustAllCerts,
        String ldapAuthTrustedCertPem,
        String ldapAuthBindDn,
        boolean ldapAuthBindPasswordConfigured,
        String ldapAuthUserSearchBase,
        String ldapAuthBindDnPattern,

        // OIDC auth provider
        String oidcIssuerUrl,
        String oidcClientId,
        boolean oidcClientSecretConfigured,
        String oidcScopes,
        String oidcUsernameClaim,

        // SIEM / syslog export
        boolean siemEnabled,
        SiemProtocol siemProtocol,
        String siemHost,
        Integer siemPort,
        SiemFormat siemFormat,
        boolean siemAuthTokenConfigured,
        String webhookUrl,
        boolean webhookAuthHeaderConfigured,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
