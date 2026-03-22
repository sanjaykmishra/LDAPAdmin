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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserTemplateServiceTest {

    @Mock private UserTemplateRepository templateRepo;
    @Mock private UserTemplateAttributeConfigRepository configRepo;
    @Mock private DirectoryConnectionRepository directoryRepo;

    private UserTemplateService service;

    private final UUID templateId = UUID.randomUUID();
    private final UUID dirId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserTemplateService(templateRepo, configRepo, directoryRepo);
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    void create_withDirectoryId_setsDirectoryConnection() {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(dirId);
        when(directoryRepo.findById(dirId)).thenReturn(Optional.of(dir));
        when(templateRepo.save(any())).thenAnswer(inv -> {
            UserTemplate t = inv.getArgument(0);
            t.setId(templateId);
            return t;
        });
        UserTemplateResponse resp = service.create(new UserTemplateRequest(
                dirId, List.of("inetOrgPerson"), "Standard Template", true, List.of()));

        assertThat(resp.directoryId()).isEqualTo(dirId);
        ArgumentCaptor<UserTemplate> captor = ArgumentCaptor.forClass(UserTemplate.class);
        verify(templateRepo).save(captor.capture());
        assertThat(captor.getValue().getDirectoryConnection()).isEqualTo(dir);
    }

    @Test
    void create_withNullDirectoryId_setsNullDirectory() {
        when(templateRepo.save(any())).thenAnswer(inv -> {
            UserTemplate t = inv.getArgument(0);
            t.setId(templateId);
            return t;
        });
        UserTemplateResponse resp = service.create(new UserTemplateRequest(
                null, List.of("inetOrgPerson"), "Standard Template", true, List.of()));

        assertThat(resp.directoryId()).isNull();
        ArgumentCaptor<UserTemplate> captor = ArgumentCaptor.forClass(UserTemplate.class);
        verify(templateRepo).save(captor.capture());
        assertThat(captor.getValue().getDirectoryConnection()).isNull();
        verify(directoryRepo, never()).findById(any());
    }

    @Test
    void create_withInvalidDirectoryId_throwsResourceNotFound() {
        UUID badId = UUID.randomUUID();
        when(directoryRepo.findById(badId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new UserTemplateRequest(
                badId, List.of("inetOrgPerson"), "Template", true, List.of())))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(templateRepo, never()).save(any());
    }

    @Test
    void create_withAttributeConfigs_savesConfigs() {
        when(templateRepo.save(any())).thenAnswer(inv -> {
            UserTemplate t = inv.getArgument(0);
            t.setId(templateId);
            return t;
        });
        UserTemplateAttributeConfig savedConfig = new UserTemplateAttributeConfig();
        savedConfig.setId(UUID.randomUUID());
        savedConfig.setAttributeName("cn");
        savedConfig.setInputType(InputType.TEXT);
        savedConfig.setRequiredOnCreate(true);
        savedConfig.setEditableOnCreate(true);
        when(configRepo.saveAll(any())).thenReturn(List.of(savedConfig));

        var configEntry = new UserTemplateRequest.AttributeConfigEntry(
                "cn", "Common Name", true, true, "TEXT", true, null, null, false);

        UserTemplateResponse resp = service.create(new UserTemplateRequest(
                null, List.of("inetOrgPerson"), "Template", true, List.of(configEntry)));

        assertThat(resp.attributeConfigs()).hasSize(1);
        verify(configRepo).saveAll(any());
    }

    @Test
    void create_withMultipleObjectClasses_storesAll() {
        when(templateRepo.save(any())).thenAnswer(inv -> {
            UserTemplate t = inv.getArgument(0);
            t.setId(templateId);
            return t;
        });
        UserTemplateResponse resp = service.create(new UserTemplateRequest(
                null, List.of("inetOrgPerson", "posixAccount"), "Multi-Class Template", true, List.of()));

        assertThat(resp.objectClassNames()).containsExactly("inetOrgPerson", "posixAccount");
        ArgumentCaptor<UserTemplate> captor = ArgumentCaptor.forClass(UserTemplate.class);
        verify(templateRepo).save(captor.capture());
        assertThat(captor.getValue().getObjectClassNames()).containsExactly("inetOrgPerson", "posixAccount");
    }

    // ── update ──────────────────────────────────────────────────────────────

    @Test
    void update_changesObjectClassNames() {
        UserTemplate existing = new UserTemplate();
        existing.setId(templateId);
        existing.setObjectClassNames(new ArrayList<>(List.of("person")));
        existing.setTemplateName("Old");
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(existing));

        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(dirId);
        when(directoryRepo.findById(dirId)).thenReturn(Optional.of(dir));
        when(templateRepo.save(any())).thenReturn(existing);

        service.update(templateId, new UserTemplateRequest(
                dirId, List.of("inetOrgPerson", "posixAccount"), "Updated Template", true, List.of()));

        assertThat(existing.getDirectoryConnection()).isEqualTo(dir);
        assertThat(existing.getObjectClassNames()).containsExactly("inetOrgPerson", "posixAccount");
    }

    @Test
    void update_notFound_throwsResourceNotFound() {
        when(templateRepo.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(templateId, new UserTemplateRequest(
                null, List.of("inetOrgPerson"), "Template", true, List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── list ────────────────────────────────────────────────────────────────

    @Test
    void list_returnsMappedTemplates() {
        UserTemplate t = new UserTemplate();
        t.setId(templateId);
        t.setObjectClassNames(new ArrayList<>(List.of("inetOrgPerson")));
        t.setTemplateName("Test");
        when(templateRepo.findAll()).thenReturn(List.of(t));
        when(configRepo.findAllByUserTemplateId(templateId)).thenReturn(List.of());

        List<UserTemplateResponse> result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).templateName()).isEqualTo("Test");
        assertThat(result.get(0).directoryId()).isNull();
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_success() {
        UserTemplate t = new UserTemplate();
        t.setId(templateId);
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(t));

        service.delete(templateId);

        verify(configRepo).deleteAllByUserTemplateId(templateId);
        verify(templateRepo).delete(t);
    }

    @Test
    void delete_notFound_throwsResourceNotFound() {
        when(templateRepo.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(templateId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
