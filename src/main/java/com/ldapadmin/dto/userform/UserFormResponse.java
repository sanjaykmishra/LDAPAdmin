package com.ldapadmin.dto.userform;

import com.ldapadmin.entity.UserForm;
import com.ldapadmin.entity.UserFormAttributeConfig;

import java.util.List;
import java.util.UUID;

/** Read DTO for a user form with its attribute configs. */
public record UserFormResponse(
        UUID id,
        String objectClassName,
        String formName,
        List<AttributeConfigEntry> attributeConfigs) {

    public record AttributeConfigEntry(
            UUID id,
            String attributeName,
            String customLabel,
            boolean requiredOnCreate,
            boolean editableOnCreate,
            String inputType) {

        public static AttributeConfigEntry from(UserFormAttributeConfig c) {
            return new AttributeConfigEntry(
                    c.getId(),
                    c.getAttributeName(),
                    c.getCustomLabel(),
                    c.isRequiredOnCreate(),
                    c.isEditableOnCreate(),
                    c.getInputType().name());
        }
    }

    public static UserFormResponse from(UserForm f, List<UserFormAttributeConfig> configs) {
        return new UserFormResponse(
                f.getId(),
                f.getObjectClassName(),
                f.getFormName(),
                configs.stream().map(AttributeConfigEntry::from).toList());
    }
}
