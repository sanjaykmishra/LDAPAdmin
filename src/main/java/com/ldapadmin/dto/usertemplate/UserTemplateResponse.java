package com.ldapadmin.dto.usertemplate;

import com.ldapadmin.entity.UserTemplate;
import com.ldapadmin.entity.UserTemplateAttributeConfig;

import java.util.List;
import java.util.UUID;

/** Read DTO for a user template with its attribute configs. */
public record UserTemplateResponse(
        UUID id,
        UUID directoryId,
        List<String> objectClassNames,
        String templateName,
        boolean showDnField,
        List<AttributeConfigEntry> attributeConfigs) {

    public record AttributeConfigEntry(
            UUID id,
            String attributeName,
            String customLabel,
            boolean requiredOnCreate,
            boolean editableOnCreate,
            String inputType,
            boolean rdn,
            String sectionName,
            int columnSpan,
            boolean hidden) {

        public static AttributeConfigEntry from(UserTemplateAttributeConfig c) {
            return new AttributeConfigEntry(
                    c.getId(),
                    c.getAttributeName(),
                    c.getCustomLabel(),
                    c.isRequiredOnCreate(),
                    c.isEditableOnCreate(),
                    c.getInputType().name(),
                    c.isRdn(),
                    c.getSectionName(),
                    c.getColumnSpan(),
                    c.isHidden());
        }
    }

    public static UserTemplateResponse from(UserTemplate t, List<UserTemplateAttributeConfig> configs) {
        return new UserTemplateResponse(
                t.getId(),
                t.getDirectoryConnection() != null ? t.getDirectoryConnection().getId() : null,
                List.copyOf(t.getObjectClassNames()),
                t.getTemplateName(),
                t.isShowDnField(),
                configs.stream().map(AttributeConfigEntry::from).toList());
    }
}
