package com.ldapadmin.service;

import com.ldapadmin.dto.settings.ApplicationSettingsDto;
import com.ldapadmin.dto.settings.UpdateApplicationSettingsRequest;
import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.repository.ApplicationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for global application settings (singleton row in {@code application_settings}).
 *
 * <h3>Single-row invariant</h3>
 * <p>There is exactly one settings row for the entire installation.  The service
 * performs an upsert: if no row exists it inserts one; otherwise it updates it.</p>
 *
 * <h3>Password handling</h3>
 * <p>SMTP and S3 credentials are stored AES-256 encrypted.  The read DTO
 * never exposes the ciphertext; instead it returns boolean flags
 * ({@code smtpPasswordConfigured}, {@code s3SecretKeyConfigured}).  On write,
 * a {@code null} password preserves the existing credential, an empty string
 * clears it, and any other value replaces it after encryption.</p>
 */
@Service
@RequiredArgsConstructor
public class ApplicationSettingsService {

    private final ApplicationSettingsRepository settingsRepo;
    private final EncryptionService             encryptionService;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the global settings, or a DTO containing only the system defaults
     * if no settings row has been persisted yet.
     */
    @Transactional(readOnly = true)
    public ApplicationSettingsDto get() {
        return settingsRepo.findFirst()
                .map(this::toDto)
                .orElseGet(this::defaultDto);
    }

    /**
     * Creates or replaces the global settings.
     */
    @Transactional
    public ApplicationSettingsDto upsert(UpdateApplicationSettingsRequest req) {
        ApplicationSettings settings = settingsRepo.findFirst()
                .orElseGet(ApplicationSettings::new);

        applyRequest(settings, req);
        return toDto(settingsRepo.save(settings));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void applyRequest(ApplicationSettings s, UpdateApplicationSettingsRequest req) {
        s.setAppName(req.appName());
        s.setLogoUrl(req.logoUrl());
        s.setPrimaryColour(req.primaryColour());
        s.setSecondaryColour(req.secondaryColour());
        s.setSessionTimeoutMinutes(req.sessionTimeoutMinutes());

        // SMTP
        s.setSmtpHost(req.smtpHost());
        s.setSmtpPort(req.smtpPort());
        s.setSmtpSenderAddress(req.smtpSenderAddress());
        s.setSmtpUsername(req.smtpUsername());
        s.setSmtpUseTls(req.smtpUseTls());
        applyPassword(req.smtpPassword(), s::getSmtpPasswordEncrypted, s::setSmtpPasswordEncrypted);

        // S3
        s.setS3EndpointUrl(req.s3EndpointUrl());
        s.setS3BucketName(req.s3BucketName());
        s.setS3AccessKey(req.s3AccessKey());
        s.setS3Region(req.s3Region());
        s.setS3PresignedUrlTtlHours(req.s3PresignedUrlTtlHours());
        applyPassword(req.s3SecretKey(), s::getS3SecretKeyEncrypted, s::setS3SecretKeyEncrypted);
    }

    /**
     * Applies password update semantics:
     * <ul>
     *   <li>{@code null}   → keep existing encrypted value</li>
     *   <li>empty string   → clear (set to null)</li>
     *   <li>any other value → encrypt and store</li>
     * </ul>
     */
    private void applyPassword(String newPlaintext,
                                java.util.function.Supplier<String> getEncrypted,
                                java.util.function.Consumer<String> setEncrypted) {
        if (newPlaintext == null) {
            // preserve existing
        } else if (newPlaintext.isEmpty()) {
            setEncrypted.accept(null);
        } else {
            setEncrypted.accept(encryptionService.encrypt(newPlaintext));
        }
    }

    private ApplicationSettingsDto toDto(ApplicationSettings s) {
        return new ApplicationSettingsDto(
                s.getId(),
                s.getAppName(),
                s.getLogoUrl(),
                s.getPrimaryColour(),
                s.getSecondaryColour(),
                s.getSessionTimeoutMinutes(),
                s.getSmtpHost(),
                s.getSmtpPort(),
                s.getSmtpSenderAddress(),
                s.getSmtpUsername(),
                s.getSmtpPasswordEncrypted() != null,
                s.isSmtpUseTls(),
                s.getS3EndpointUrl(),
                s.getS3BucketName(),
                s.getS3AccessKey(),
                s.getS3SecretKeyEncrypted() != null,
                s.getS3Region(),
                s.getS3PresignedUrlTtlHours(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }

    private ApplicationSettingsDto defaultDto() {
        return new ApplicationSettingsDto(
                null,
                "LDAP Portal", null, null, null,
                60,
                null, 587, null, null, false, true,
                null, null, null, false, null, 24,
                null, null);
    }
}
