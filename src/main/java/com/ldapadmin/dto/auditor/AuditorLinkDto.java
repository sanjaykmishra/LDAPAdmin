package com.ldapadmin.dto.auditor;

import com.ldapadmin.entity.AuditorLink;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for an auditor link, returned to admin users.
 */
public record AuditorLinkDto(
        UUID id,
        UUID directoryId,
        String token,
        String label,
        List<UUID> campaignIds,
        boolean includeSod,
        boolean includeEntitlements,
        boolean includeAuditEvents,
        OffsetDateTime dataFrom,
        OffsetDateTime dataTo,
        OffsetDateTime expiresAt,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime lastAccessedAt,
        int accessCount,
        boolean revoked,
        OffsetDateTime revokedAt
) {
    public static AuditorLinkDto from(AuditorLink link) {
        return new AuditorLinkDto(
                link.getId(),
                link.getDirectory().getId(),
                link.getToken(),
                link.getLabel(),
                link.getCampaignIds(),
                link.isIncludeSod(),
                link.isIncludeEntitlements(),
                link.isIncludeAuditEvents(),
                link.getDataFrom(),
                link.getDataTo(),
                link.getExpiresAt(),
                link.getCreatedBy().getUsername(),
                link.getCreatedAt(),
                link.getLastAccessedAt(),
                link.getAccessCount(),
                link.isRevoked(),
                link.getRevokedAt()
        );
    }
}
