package com.ldapadmin.dto.profile;

import com.ldapadmin.entity.enums.InputType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Write DTO for one entry within a create/update profile request.
 */
public record UpsertAttributeProfileEntryRequest(
        @NotBlank String attributeName,
        String customLabel,
        boolean requiredOnCreate,
        boolean enabledOnEdit,
        InputType inputType,
        @Min(0) int displayOrder,
        boolean visibleInListView) {}
