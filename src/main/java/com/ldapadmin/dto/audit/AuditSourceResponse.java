package com.ldapadmin.dto.audit;

import com.ldapadmin.entity.AuditDataSource;
import com.ldapadmin.entity.enums.SslMode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditSourceResponse(
        UUID id,
        String displayName,
        String host,
        int port,
        SslMode sslMode,
        boolean trustAllCerts,
        String bindDn,
        String changelogBaseDn,
        String branchFilterDn,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AuditSourceResponse from(AuditDataSource src) {
        return new AuditSourceResponse(
                src.getId(),
                src.getDisplayName(),
                src.getHost(),
                src.getPort(),
                src.getSslMode(),
                src.isTrustAllCerts(),
                src.getBindDn(),
                src.getChangelogBaseDn(),
                src.getBranchFilterDn(),
                src.isEnabled(),
                src.getCreatedAt(),
                src.getUpdatedAt()
        );
    }
}
