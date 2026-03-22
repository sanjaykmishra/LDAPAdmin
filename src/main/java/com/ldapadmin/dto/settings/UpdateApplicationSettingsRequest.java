package com.ldapadmin.dto.settings;

import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.entity.enums.SslMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Write DTO for creating or replacing application settings.
 *
 * <p>Password fields ({@code smtpPassword}, {@code s3SecretKey}, etc.) are optional.
 * Passing {@code null} preserves the existing stored credential; passing an
 * empty string clears it.</p>
 */
public record UpdateApplicationSettingsRequest(
        // Branding
        @NotBlank String appName,
        String logoUrl,
        String primaryColour,
        String secondaryColour,

        // Approval workflow
        boolean superadminBypassApproval,

        // Session
        @NotNull @Min(1) Integer sessionTimeoutMinutes,

        // SMTP
        String smtpHost,
        Integer smtpPort,
        String smtpSenderAddress,
        String smtpUsername,
        /** null = keep existing; empty string = clear */
        String smtpPassword,
        boolean smtpUseTls,

        // S3
        String s3EndpointUrl,
        String s3BucketName,
        String s3AccessKey,
        /** null = keep existing; empty string = clear */
        String s3SecretKey,
        String s3Region,
        @Min(1) int s3PresignedUrlTtlHours,

        // Authentication
        Set<AccountType> enabledAuthTypes,

        // LDAP auth provider
        String ldapAuthHost,
        Integer ldapAuthPort,
        SslMode ldapAuthSslMode,
        Boolean ldapAuthTrustAllCerts,
        String ldapAuthTrustedCertPem,
        String ldapAuthBindDn,
        /** null = keep existing; empty string = clear */
        String ldapAuthBindPassword,
        String ldapAuthUserSearchBase,
        String ldapAuthBindDnPattern,

        // OIDC auth provider
        String oidcIssuerUrl,
        String oidcClientId,
        /** null = keep existing; empty string = clear */
        String oidcClientSecret,
        String oidcScopes,
        String oidcUsernameClaim) {}
