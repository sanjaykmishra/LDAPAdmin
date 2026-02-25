package com.ldapadmin.dto.settings;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Write DTO for creating or replacing application settings.
 *
 * <p>Password fields ({@code smtpPassword}, {@code s3SecretKey}) are optional.
 * Passing {@code null} preserves the existing stored credential; passing an
 * empty string clears it.</p>
 */
public record UpdateApplicationSettingsRequest(
        // Branding
        @NotBlank String appName,
        String logoUrl,
        String primaryColour,
        String secondaryColour,

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
        @Min(1) int s3PresignedUrlTtlHours) {}
