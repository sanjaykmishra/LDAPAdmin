package com.ldapadmin.dto.settings;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read DTO for per-tenant application settings.
 * Encrypted credential fields are never returned; instead boolean flags
 * indicate whether a password/secret has been configured.
 */
public record ApplicationSettingsDto(
        UUID id,
        UUID tenantId,

        // Branding
        String appName,
        String logoUrl,
        String primaryColour,
        String secondaryColour,

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

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
