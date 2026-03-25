package com.ldapadmin.dto.hr;

import com.ldapadmin.entity.enums.HrProvider;
import com.ldapadmin.entity.hr.HrConnection;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HrConnectionDto(
        UUID id,
        UUID directoryId,
        HrProvider provider,
        String displayName,
        boolean enabled,
        String subdomain,
        boolean hasApiKey,
        String matchAttribute,
        String matchField,
        String syncCron,
        OffsetDateTime lastSyncAt,
        String lastSyncStatus,
        String lastSyncMessage,
        Integer lastSyncEmployeeCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static HrConnectionDto from(HrConnection c) {
        return new HrConnectionDto(
                c.getId(),
                c.getDirectory().getId(),
                c.getProvider(),
                c.getDisplayName(),
                c.isEnabled(),
                c.getSubdomain(),
                c.getApiKeyEncrypted() != null,
                c.getMatchAttribute(),
                c.getMatchField(),
                c.getSyncCron(),
                c.getLastSyncAt(),
                c.getLastSyncStatus(),
                c.getLastSyncMessage(),
                c.getLastSyncEmployeeCount(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
