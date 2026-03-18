package com.ldapadmin.service;

import com.ldapadmin.dto.realm.RealmRequest;
import com.ldapadmin.dto.realm.RealmResponse;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.RealmObjectclass;
import com.ldapadmin.entity.UserForm;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.RealmObjectclassRepository;
import com.ldapadmin.repository.RealmRepository;
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
class RealmServiceTest {

    @Mock private RealmRepository               realmRepo;
    @Mock private DirectoryConnectionRepository dirRepo;
    @Mock private RealmObjectclassRepository    realmOcRepo;
    @Mock private UserFormRepository            userFormRepo;

    private RealmService service;

    private final UUID dirId   = UUID.randomUUID();
    private final UUID realmId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RealmService(realmRepo, dirRepo, realmOcRepo, userFormRepo);
        lenient().when(realmOcRepo.findAllByRealmId(any())).thenReturn(List.of());
    }

    // ── listByDirectory ───────────────────────────────────────────────────────

    @Test
    void listByDirectory_returnsRealms() {
        DirectoryConnection dir = mockDirectory();
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dir));
        when(realmRepo.findAllByDirectoryIdOrderByDisplayOrderAsc(dirId))
                .thenReturn(List.of(mockRealm(dir)));

        List<RealmResponse> result = service.listByDirectory(dirId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("users");
        assertThat(result.get(0).directoryId()).isEqualTo(dirId);
    }

    @Test
    void listByDirectory_unknownDirectory_throwsNotFound() {
        when(dirRepo.findById(dirId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByDirectory(dirId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_returnsRealm() {
        DirectoryConnection dir = mockDirectory();
        when(realmRepo.findByIdAndDirectoryId(realmId, dirId))
                .thenReturn(Optional.of(mockRealm(dir)));

        RealmResponse resp = service.get(dirId, realmId);

        assertThat(resp.id()).isEqualTo(realmId);
        assertThat(resp.name()).isEqualTo("users");
    }

    @Test
    void get_notFound_throwsResourceNotFound() {
        when(realmRepo.findByIdAndDirectoryId(realmId, dirId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(dirId, realmId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesAndReturnsRealm() {
        DirectoryConnection dir = mockDirectory();
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dir));
        when(realmRepo.save(any(Realm.class))).thenAnswer(inv -> {
            Realm r = inv.getArgument(0);
            r.setId(realmId);
            return r;
        });

        RealmResponse resp = service.create(dirId, basicRequest("employees"));

        assertThat(resp.name()).isEqualTo("employees");
        assertThat(resp.userBaseDn()).isEqualTo("ou=people,dc=corp,dc=com");
        ArgumentCaptor<Realm> captor = ArgumentCaptor.forClass(Realm.class);
        verify(realmRepo).save(captor.capture());
        assertThat(captor.getValue().getDirectory()).isSameAs(dir);
    }

    @Test
    void create_withUserForms_linksMultipleForms() {
        DirectoryConnection dir = mockDirectory();
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dir));
        when(realmRepo.save(any(Realm.class))).thenAnswer(inv -> {
            Realm r = inv.getArgument(0);
            r.setId(realmId);
            return r;
        });

        UUID formId1 = UUID.randomUUID();
        UUID formId2 = UUID.randomUUID();
        UserForm form1 = new UserForm();
        form1.setId(formId1);
        form1.setFormName("Person Form");
        form1.setObjectClassNames(new java.util.ArrayList<>(List.of("inetOrgPerson")));
        UserForm form2 = new UserForm();
        form2.setId(formId2);
        form2.setFormName("Service Account Form");
        form2.setObjectClassNames(new java.util.ArrayList<>(List.of("account")));
        when(userFormRepo.findById(formId1)).thenReturn(Optional.of(form1));
        when(userFormRepo.findById(formId2)).thenReturn(Optional.of(form2));

        RealmRequest req = new RealmRequest(
                "employees", "ou=people,dc=corp,dc=com", "ou=groups,dc=corp,dc=com",
                0, List.of(formId1, formId2));

        service.create(dirId, req);

        verify(realmOcRepo, times(2)).save(any(RealmObjectclass.class));
    }

    @Test
    void create_unknownDirectory_throwsNotFound() {
        when(dirRepo.findById(dirId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dirId, basicRequest("employees")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_replacesFields() {
        DirectoryConnection dir = mockDirectory();
        Realm existing = mockRealm(dir);
        when(realmRepo.findByIdAndDirectoryId(realmId, dirId)).thenReturn(Optional.of(existing));
        when(realmRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RealmRequest req = new RealmRequest(
                "renamed", "ou=staff,dc=corp,dc=com", "ou=teams,dc=corp,dc=com",
                2, List.of());

        RealmResponse resp = service.update(dirId, realmId, req);

        assertThat(resp.name()).isEqualTo("renamed");
        assertThat(resp.userBaseDn()).isEqualTo("ou=staff,dc=corp,dc=com");
    }

    @Test
    void update_notFound_throwsResourceNotFound() {
        when(realmRepo.findByIdAndDirectoryId(realmId, dirId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(dirId, realmId, basicRequest("x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_callsRepoDelete() {
        DirectoryConnection dir = mockDirectory();
        Realm existing = mockRealm(dir);
        when(realmRepo.findByIdAndDirectoryId(realmId, dirId)).thenReturn(Optional.of(existing));

        service.delete(dirId, realmId);

        verify(realmRepo).delete(existing);
    }

    @Test
    void delete_notFound_throwsResourceNotFound() {
        when(realmRepo.findByIdAndDirectoryId(realmId, dirId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(dirId, realmId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DirectoryConnection mockDirectory() {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(dirId);
        return dir;
    }

    private Realm mockRealm(DirectoryConnection dir) {
        Realm r = new Realm();
        r.setId(realmId);
        r.setDirectory(dir);
        r.setName("users");
        r.setUserBaseDn("ou=people,dc=corp,dc=com");
        r.setGroupBaseDn("ou=groups,dc=corp,dc=com");
        r.setDisplayOrder(0);
        return r;
    }

    private RealmRequest basicRequest(String name) {
        return new RealmRequest(
                name,
                "ou=people,dc=corp,dc=com",
                "ou=groups,dc=corp,dc=com",
                0,
                null);
    }
}
