package com.ldapadmin.dto.userform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Create / update request for a user form and its attribute configs.
 */
public record UserFormRequest(
        UUID directoryId,
        @NotBlank @Size(max = 255) String objectClassName,
        @NotBlank @Size(max = 255) String formName,
        List<@Valid AttributeConfigEntry> attributeConfigs) {

    public record AttributeConfigEntry(
            @NotBlank @Size(max = 255) String attributeName,
            @Size(max = 255) String customLabel,
            boolean requiredOnCreate,
            boolean editableOnCreate,
            @NotNull String inputType) {
    }
}
