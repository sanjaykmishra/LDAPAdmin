package com.ldapadmin.service;

import com.ldapadmin.dto.userform.UserFormRequest;
import com.ldapadmin.dto.userform.UserFormResponse;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.UserForm;
import com.ldapadmin.entity.UserFormAttributeConfig;
import com.ldapadmin.entity.enums.InputType;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.UserFormAttributeConfigRepository;
import com.ldapadmin.repository.UserFormRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserFormServiceTest {

    @Mock private UserFormRepository formRepo;
    @Mock private UserFormAttributeConfigRepository configRepo;
    @Mock private DirectoryConnectionRepository directoryRepo;

    private UserFormService service;

    private final UUID formId = UUID.randomUUID();
    private final UUID dirId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserFormService(formRepo, configRepo, directoryRepo);
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    void create_withDirectoryId_setsDirectoryConnection() {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(dirId);
        when(directoryRepo.findById(dirId)).thenReturn(Optional.of(dir));
        when(formRepo.save(any())).thenAnswer(inv -> {
            UserForm f = inv.getArgument(0);
            f.setId(formId);
            return f;
        });
        UserFormResponse resp = service.create(new UserFormRequest(
                dirId, "inetOrgPerson", "Standard Form", List.of()));

        assertThat(resp.directoryId()).isEqualTo(dirId);
        ArgumentCaptor<UserForm> captor = ArgumentCaptor.forClass(UserForm.class);
        verify(formRepo).save(captor.capture());
        assertThat(captor.getValue().getDirectoryConnection()).isEqualTo(dir);
    }

    @Test
    void create_withNullDirectoryId_setsNullDirectory() {
        when(formRepo.save(any())).thenAnswer(inv -> {
            UserForm f = inv.getArgument(0);
            f.setId(formId);
            return f;
        });
        UserFormResponse resp = service.create(new UserFormRequest(
                null, "inetOrgPerson", "Standard Form", List.of()));

        assertThat(resp.directoryId()).isNull();
        ArgumentCaptor<UserForm> captor = ArgumentCaptor.forClass(UserForm.class);
        verify(formRepo).save(captor.capture());
        assertThat(captor.getValue().getDirectoryConnection()).isNull();
        verify(directoryRepo, never()).findById(any());
    }

    @Test
    void create_withInvalidDirectoryId_throwsResourceNotFound() {
        UUID badId = UUID.randomUUID();
        when(directoryRepo.findById(badId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new UserFormRequest(
                badId, "inetOrgPerson", "Form", List.of())))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(formRepo, never()).save(any());
    }

    @Test
    void create_withAttributeConfigs_savesConfigs() {
        when(formRepo.save(any())).thenAnswer(inv -> {
            UserForm f = inv.getArgument(0);
            f.setId(formId);
            return f;
        });
        UserFormAttributeConfig savedConfig = new UserFormAttributeConfig();
        savedConfig.setId(UUID.randomUUID());
        savedConfig.setAttributeName("cn");
        savedConfig.setInputType(InputType.TEXT);
        savedConfig.setRequiredOnCreate(true);
        savedConfig.setEditableOnCreate(true);
        when(configRepo.saveAll(any())).thenReturn(List.of(savedConfig));

        var configEntry = new UserFormRequest.AttributeConfigEntry(
                "cn", "Common Name", true, true, "TEXT", true);

        UserFormResponse resp = service.create(new UserFormRequest(
                null, "inetOrgPerson", "Form", List.of(configEntry)));

        assertThat(resp.attributeConfigs()).hasSize(1);
        verify(configRepo).saveAll(any());
    }

    // ── update ──────────────────────────────────────────────────────────────

    @Test
    void update_changesDirectoryId() {
        UserForm existing = new UserForm();
        existing.setId(formId);
        existing.setObjectClassName("person");
        existing.setFormName("Old");
        when(formRepo.findById(formId)).thenReturn(Optional.of(existing));

        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(dirId);
        when(directoryRepo.findById(dirId)).thenReturn(Optional.of(dir));
        when(formRepo.save(any())).thenReturn(existing);

        service.update(formId, new UserFormRequest(
                dirId, "inetOrgPerson", "Updated Form", List.of()));

        assertThat(existing.getDirectoryConnection()).isEqualTo(dir);
        assertThat(existing.getObjectClassName()).isEqualTo("inetOrgPerson");
    }

    @Test
    void update_notFound_throwsResourceNotFound() {
        when(formRepo.findById(formId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(formId, new UserFormRequest(
                null, "inetOrgPerson", "Form", List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── list ────────────────────────────────────────────────────────────────

    @Test
    void list_returnsMappedForms() {
        UserForm f = new UserForm();
        f.setId(formId);
        f.setObjectClassName("inetOrgPerson");
        f.setFormName("Test");
        when(formRepo.findAll()).thenReturn(List.of(f));
        when(configRepo.findAllByUserFormId(formId)).thenReturn(List.of());

        List<UserFormResponse> result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).formName()).isEqualTo("Test");
        assertThat(result.get(0).directoryId()).isNull();
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_success() {
        UserForm f = new UserForm();
        f.setId(formId);
        when(formRepo.findById(formId)).thenReturn(Optional.of(f));

        service.delete(formId);

        verify(configRepo).deleteAllByUserFormId(formId);
        verify(formRepo).delete(f);
    }

    @Test
    void delete_notFound_throwsResourceNotFound() {
        when(formRepo.findById(formId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(formId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
