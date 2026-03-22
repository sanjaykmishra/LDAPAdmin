package com.ldapadmin.dto.profile;

import com.ldapadmin.dto.profile.CreateProfileRequest.AttributeConfigEntry;
import com.ldapadmin.dto.profile.CreateProfileRequest.GroupAssignmentEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 500) String targetOuDn,
        @NotEmpty List<@NotBlank String> objectClassNames,
        @NotBlank @Size(max = 100) String rdnAttribute,
        boolean showDnField,
        boolean enabled,
        boolean selfRegistrationAllowed,
        List<@Valid AttributeConfigEntry> attributeConfigs,
        List<@Valid GroupAssignmentEntry> groupAssignments) {
}
