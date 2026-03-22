package com.ldapadmin.service;

import com.ldapadmin.dto.usertemplate.UserTemplateRequest;
import com.ldapadmin.dto.usertemplate.UserTemplateResponse;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.UserTemplate;
import com.ldapadmin.entity.UserTemplateAttributeConfig;
import com.ldapadmin.entity.enums.InputType;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.UserTemplateAttributeConfigRepository;
import com.ldapadmin.repository.UserTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for user template definitions and their attribute configs.
 */
@Service
@RequiredArgsConstructor
public class UserTemplateService {

    private final UserTemplateRepository                templateRepo;
    private final UserTemplateAttributeConfigRepository configRepo;
    private final DirectoryConnectionRepository         directoryRepo;

    @Transactional(readOnly = true)
    public List<UserTemplateResponse> list() {
        return templateRepo.findAll().stream()
                .map(t -> UserTemplateResponse.from(t, configRepo.findAllByUserTemplateIdOrderByDisplayOrderAsc(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserTemplateResponse get(UUID id) {
        UserTemplate template = requireTemplate(id);
        return UserTemplateResponse.from(template, configRepo.findAllByUserTemplateIdOrderByDisplayOrderAsc(id));
    }

    @Transactional
    public UserTemplateResponse create(UserTemplateRequest req) {
        validateRdn(req.attributeConfigs());
        UserTemplate template = new UserTemplate();
        template.setDirectoryConnection(resolveDirectory(req.directoryId()));
        template.setObjectClassNames(new ArrayList<>(req.objectClassNames()));
        template.setTemplateName(req.templateName());
        template.setShowDnField(req.showDnField());
        template = templateRepo.save(template);

        List<UserTemplateAttributeConfig> configs = saveConfigs(template, req.attributeConfigs());
        return UserTemplateResponse.from(template, configs);
    }

    @Transactional
    public UserTemplateResponse update(UUID id, UserTemplateRequest req) {
        validateRdn(req.attributeConfigs());
        UserTemplate template = requireTemplate(id);
        template.setDirectoryConnection(resolveDirectory(req.directoryId()));
        template.setObjectClassNames(new ArrayList<>(req.objectClassNames()));
        template.setTemplateName(req.templateName());
        template.setShowDnField(req.showDnField());
        template = templateRepo.save(template);

        configRepo.deleteAllByUserTemplateId(id);
        configRepo.flush();
        List<UserTemplateAttributeConfig> configs = saveConfigs(template, req.attributeConfigs());
        return UserTemplateResponse.from(template, configs);
    }

    @Transactional
    public void delete(UUID id) {
        UserTemplate template = requireTemplate(id);
        configRepo.deleteAllByUserTemplateId(id);
        templateRepo.delete(template);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private UserTemplate requireTemplate(UUID id) {
        return templateRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserTemplate", id));
    }

    private DirectoryConnection resolveDirectory(UUID directoryId) {
        if (directoryId == null) return null;
        return directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
    }

    private void validateRdn(List<UserTemplateRequest.AttributeConfigEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        long rdnCount = entries.stream().filter(UserTemplateRequest.AttributeConfigEntry::rdn).count();
        if (rdnCount == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Exactly one attribute must be designated as the RDN attribute");
        }
        if (rdnCount > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only one attribute can be the RDN attribute");
        }
    }

    private List<UserTemplateAttributeConfig> saveConfigs(UserTemplate template,
                                                          List<UserTemplateRequest.AttributeConfigEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<UserTemplateAttributeConfig> configs = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            UserTemplateRequest.AttributeConfigEntry e = entries.get(i);
            UserTemplateAttributeConfig c = new UserTemplateAttributeConfig();
            c.setUserTemplate(template);
            c.setAttributeName(e.attributeName());
            c.setCustomLabel(e.customLabel());
            c.setRequiredOnCreate(e.rdn() || e.requiredOnCreate());
            c.setEditableOnCreate(e.editableOnCreate());
            c.setInputType(InputType.valueOf(e.inputType()));
            c.setRdn(e.rdn());
            c.setSectionName(e.sectionName());
            c.setColumnSpan(e.columnSpan() != null ? e.columnSpan() : 3);
            c.setHidden(e.hidden());
            c.setDisplayOrder(i);
            configs.add(c);
        }
        return configRepo.saveAll(configs);
    }
}
