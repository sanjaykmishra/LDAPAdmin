package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.settings.ApplicationSettingsDto;
import com.ldapadmin.dto.settings.UpdateApplicationSettingsRequest;
import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.ApplicationSettingsRepository;
import com.ldapadmin.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * CRUD for per-tenant application settings (§10.2).
 *
 * <h3>One-row-per-tenant invariant</h3>
 * <p>The DB enforces a unique constraint on {@code tenant_id}.  The service
 * performs an upsert: if no row exists for the tenant it inserts one;
 * otherwise it updates the existing row.</p>
 *
 * <h3>Password handling</h3>
 * <p>SMTP and S3 credentials are stored AES-256 encrypted.  The read DTO
 * never exposes the ciphertext; instead it returns boolean flags
 * ({@code smtpPasswordConfigured}, {@code s3SecretKeyConfigured}).  On write,
 * a {@code null} password preserves the existing credential, an empty string
 * clears it, and any other value replaces it after encryption.</p>
 *
 * <h3>Tenant isolation</h3>
 * <p>Tenant admins operate on their own tenant's settings.  Superadmins
 * supply an explicit {@code tenantId} parameter.</p>
 */
@Service
@RequiredArgsConstructor
public class ApplicationSettingsService {

    private final ApplicationSettingsRepository settingsRepo;
    private final TenantRepository             tenantRepo;
    private final EncryptionService            encryptionService;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the settings for the principal's tenant, or a DTO containing
     * only the system defaults if no settings row has been persisted yet.
     */
    @Transactional(readOnly = true)
    public ApplicationSettingsDto get(AuthPrincipal principal) {
        UUID tenantId = resolveTenantId(principal);
        return settingsRepo.findByTenantId(tenantId)
                .map(this::toDto)
                .orElseGet(() -> defaultDto(tenantId));
    }

    /**
     * Creates or replaces the settings for the principal's tenant.
     */
    @Transactional
    public ApplicationSettingsDto upsert(UpdateApplicationSettingsRequest req,
                                         AuthPrincipal principal) {
        UUID tenantId = resolveTenantId(principal);
        ApplicationSettings settings = settingsRepo.findByTenantId(tenantId)
                .orElseGet(() -> {
                    Tenant tenant = tenantRepo.findById(tenantId)
                            .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
                    ApplicationSettings s = new ApplicationSettings();
                    s.setTenant(tenant);
                    return s;
                });

        applyRequest(settings, req);
        return toDto(settingsRepo.save(settings));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private UUID resolveTenantId(AuthPrincipal principal) {
        if (principal.isSuperadmin()) {
            throw new IllegalArgumentException(
                    "Superadmins must supply an explicit tenantId");
        }
        return principal.tenantId();
    }

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
                s.getTenant().getId(),
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

    private ApplicationSettingsDto defaultDto(UUID tenantId) {
        return new ApplicationSettingsDto(
                null, tenantId,
                "LDAP Portal", null, null, null,
                60,
                null, 587, null, null, false, true,
                null, null, null, false, null, 24,
                null, null);
    }
}
