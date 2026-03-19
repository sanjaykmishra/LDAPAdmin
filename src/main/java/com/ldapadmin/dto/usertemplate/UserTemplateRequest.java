package com.ldapadmin.dto.usertemplate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Create / update request for a user template and its attribute configs.
 */
public record UserTemplateRequest(
        UUID directoryId,
        @NotEmpty List<@NotBlank @Size(max = 255) String> objectClassNames,
        @NotBlank @Size(max = 255) String templateName,
        List<@Valid AttributeConfigEntry> attributeConfigs) {

    public record AttributeConfigEntry(
            @NotBlank @Size(max = 255) String attributeName,
            @Size(max = 255) String customLabel,
            boolean requiredOnCreate,
            boolean editableOnCreate,
            @NotNull String inputType,
            boolean rdn,
            @Size(max = 255) String sectionName,
            Integer columnSpan,
            boolean hidden) {
    }
}
