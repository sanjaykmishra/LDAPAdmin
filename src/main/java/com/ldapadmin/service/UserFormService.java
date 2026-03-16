package com.ldapadmin.service;

import com.ldapadmin.dto.userform.UserFormRequest;
import com.ldapadmin.dto.userform.UserFormResponse;
import com.ldapadmin.entity.UserForm;
import com.ldapadmin.entity.UserFormAttributeConfig;
import com.ldapadmin.entity.enums.InputType;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.UserFormAttributeConfigRepository;
import com.ldapadmin.repository.UserFormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for user form definitions and their attribute configs.
 */
@Service
@RequiredArgsConstructor
public class UserFormService {

    private final UserFormRepository                formRepo;
    private final UserFormAttributeConfigRepository configRepo;

    @Transactional(readOnly = true)
    public List<UserFormResponse> list() {
        return formRepo.findAll().stream()
                .map(f -> UserFormResponse.from(f, configRepo.findAllByUserFormId(f.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserFormResponse get(UUID id) {
        UserForm form = requireForm(id);
        return UserFormResponse.from(form, configRepo.findAllByUserFormId(id));
    }

    @Transactional
    public UserFormResponse create(UserFormRequest req) {
        UserForm form = new UserForm();
        form.setObjectClassName(req.objectClassName());
        form.setFormName(req.formName());
        form = formRepo.save(form);

        List<UserFormAttributeConfig> configs = saveConfigs(form, req.attributeConfigs());
        return UserFormResponse.from(form, configs);
    }

    @Transactional
    public UserFormResponse update(UUID id, UserFormRequest req) {
        UserForm form = requireForm(id);
        form.setObjectClassName(req.objectClassName());
        form.setFormName(req.formName());
        form = formRepo.save(form);

        configRepo.deleteAllByUserFormId(id);
        configRepo.flush();
        List<UserFormAttributeConfig> configs = saveConfigs(form, req.attributeConfigs());
        return UserFormResponse.from(form, configs);
    }

    @Transactional
    public void delete(UUID id) {
        UserForm form = requireForm(id);
        configRepo.deleteAllByUserFormId(id);
        formRepo.delete(form);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private UserForm requireForm(UUID id) {
        return formRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserForm", id));
    }

    private List<UserFormAttributeConfig> saveConfigs(UserForm form,
                                                      List<UserFormRequest.AttributeConfigEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<UserFormAttributeConfig> configs = entries.stream().map(e -> {
            UserFormAttributeConfig c = new UserFormAttributeConfig();
            c.setUserForm(form);
            c.setAttributeName(e.attributeName());
            c.setCustomLabel(e.customLabel());
            c.setRequiredOnCreate(e.requiredOnCreate());
            c.setEditableOnCreate(e.editableOnCreate());
            c.setInputType(InputType.valueOf(e.inputType()));
            return c;
        }).toList();
        return configRepo.saveAll(configs);
    }
}
