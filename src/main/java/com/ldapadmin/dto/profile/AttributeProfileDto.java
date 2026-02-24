package com.ldapadmin.dto.profile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read DTO for a full attribute profile with its entries.
 */
public record AttributeProfileDto(
        UUID id,
        UUID directoryId,
        String branchDn,
        String displayName,
        boolean isDefault,
        List<AttributeProfileEntryDto> entries,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
