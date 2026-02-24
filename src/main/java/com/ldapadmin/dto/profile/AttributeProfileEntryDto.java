package com.ldapadmin.dto.profile;

import com.ldapadmin.entity.enums.InputType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read DTO for a single per-attribute configuration row.
 */
public record AttributeProfileEntryDto(
        UUID id,
        String attributeName,
        String customLabel,
        boolean requiredOnCreate,
        boolean enabledOnEdit,
        InputType inputType,
        int displayOrder,
        boolean visibleInListView,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
