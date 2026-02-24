package com.ldapadmin.dto.csv;

import com.ldapadmin.entity.enums.ConflictHandling;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a saved CSV mapping template, including its column entries.
 */
public record CsvMappingTemplateDto(
        UUID id,
        UUID directoryId,
        String name,
        String targetKeyAttribute,
        ConflictHandling conflictHandling,
        List<CsvColumnMappingDto> entries,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
