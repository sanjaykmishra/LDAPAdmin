package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.profile.AttributeProfileDto;
import com.ldapadmin.dto.profile.CreateAttributeProfileRequest;
import com.ldapadmin.dto.profile.UpsertAttributeProfileEntryRequest;
import com.ldapadmin.entity.AttributeProfile;
import com.ldapadmin.entity.AttributeProfileEntry;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.entity.enums.InputType;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AttributeProfileEntryRepository;
import com.ldapadmin.repository.AttributeProfileRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
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
class AttributeProfileServiceTest {

    @Mock private AttributeProfileRepository      profileRepo;
    @Mock private AttributeProfileEntryRepository entryRepo;
    @Mock private DirectoryConnectionRepository   dirRepo;

    private AttributeProfileService service;

    private final UUID tenantId   = UUID.randomUUID();
    private final UUID dirId      = UUID.randomUUID();
    private final UUID profileId  = UUID.randomUUID();

    private AuthPrincipal adminPrincipal;
    private AuthPrincipal superadminPrincipal;

    @BeforeEach
    void setUp() {
        service = new AttributeProfileService(profileRepo, entryRepo, dirRepo);
        adminPrincipal = new AuthPrincipal(
                PrincipalType.ADMIN, UUID.randomUUID(), tenantId, "admin");
        superadminPrincipal = new AuthPrincipal(
                PrincipalType.SUPERADMIN, UUID.randomUUID(), null, "superadmin");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DirectoryConnection mockDirectory() {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(dirId);
        dir.setTenant(tenant);
        return dir;
    }

    private AttributeProfile mockProfile(DirectoryConnection dir, String branchDn,
                                          boolean isDefault) {
        AttributeProfile p = new AttributeProfile();
        p.setId(profileId);
        p.setDirectory(dir);
        p.setTenant(dir.getTenant());
        p.setBranchDn(branchDn);
        p.setDisplayName("Test Profile");
        p.setDefault(isDefault);
        return p;
    }

    private AttributeProfileEntry mockEntry(AttributeProfile profile, String attrName,
                                             int order) {
        AttributeProfileEntry e = new AttributeProfileEntry();
        e.setId(UUID.randomUUID());
        e.setProfile(profile);
        e.setAttributeName(attrName);
        e.setInputType(InputType.TEXT);
        e.setDisplayOrder(order);
        return e;
    }

    private UpsertAttributeProfileEntryRequest entryReq(String attrName, int order) {
        return new UpsertAttributeProfileEntryRequest(
                attrName, null, false, true, InputType.TEXT, order, false);
    }

    // ── listByDirectory ───────────────────────────────────────────────────────

    @Test
    void listByDirectory_returnsProfilesWithEntries() {
        DirectoryConnection dir = mockDirectory();
        AttributeProfile profile = mockProfile(dir, "ou=people,dc=example,dc=com", false);
        AttributeProfileEntry entry = mockEntry(profile, "cn", 0);

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.findAllByDirectoryId(dirId)).thenReturn(List.of(profile));
        when(entryRepo.findAllByProfileIdOrderByDisplayOrderAsc(profileId))
                .thenReturn(List.of(entry));

        List<AttributeProfileDto> result = service.listByDirectory(dirId, adminPrincipal);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).branchDn()).isEqualTo("ou=people,dc=example,dc=com");
        assertThat(result.get(0).entries()).hasSize(1);
        assertThat(result.get(0).entries().get(0).attributeName()).isEqualTo("cn");
    }

    @Test
    void listByDirectory_superadminCanAccessAnyDirectory() {
        DirectoryConnection dir = mockDirectory();
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dir));
        when(profileRepo.findAllByDirectoryId(dirId)).thenReturn(List.of());

        List<AttributeProfileDto> result = service.listByDirectory(dirId, superadminPrincipal);

        assertThat(result).isEmpty();
        verify(dirRepo).findById(dirId);
        verify(dirRepo, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void listByDirectory_unknownDirectory_throwsNotFound() {
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByDirectory(dirId, adminPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_returnsProfileWithEntries() {
        DirectoryConnection dir = mockDirectory();
        AttributeProfile profile = mockProfile(dir, "ou=people,dc=example,dc=com", false);
        AttributeProfileEntry entryA = mockEntry(profile, "cn", 0);
        AttributeProfileEntry entryB = mockEntry(profile, "mail", 1);

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(profile));
        when(entryRepo.findAllByProfileIdOrderByDisplayOrderAsc(profileId))
                .thenReturn(List.of(entryA, entryB));

        AttributeProfileDto dto = service.getById(dirId, profileId, adminPrincipal);

        assertThat(dto.id()).isEqualTo(profileId);
        assertThat(dto.entries()).hasSize(2);
    }

    @Test
    void getById_wrongDirectory_throwsNotFound() {
        DirectoryConnection dir = mockDirectory();
        AttributeProfile profile = mockProfile(dir, "*", true);
        profile.getDirectory().setId(UUID.randomUUID()); // different dir

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.getById(dirId, profileId, adminPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesProfileAndEntries() {
        DirectoryConnection dir = mockDirectory();
        CreateAttributeProfileRequest req = new CreateAttributeProfileRequest(
                "ou=people,dc=example,dc=com",
                "People",
                false,
                List.of(entryReq("cn", 0), entryReq("mail", 1)));

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.existsByDirectoryIdAndBranchDn(dirId, req.branchDn())).thenReturn(false);
        when(profileRepo.save(any(AttributeProfile.class)))
                .thenAnswer(inv -> {
                    AttributeProfile p = inv.getArgument(0);
                    p.setId(profileId);
                    return p;
                });
        when(entryRepo.save(any(AttributeProfileEntry.class)))
                .thenAnswer(inv -> {
                    AttributeProfileEntry e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        AttributeProfileDto dto = service.create(dirId, req, adminPrincipal);

        assertThat(dto.branchDn()).isEqualTo("ou=people,dc=example,dc=com");
        assertThat(dto.displayName()).isEqualTo("People");
        assertThat(dto.isDefault()).isFalse();
        assertThat(dto.entries()).hasSize(2);
        verify(profileRepo).save(any(AttributeProfile.class));
        verify(entryRepo, times(2)).save(any(AttributeProfileEntry.class));
    }

    @Test
    void create_defaultProfile_savedWithFlag() {
        DirectoryConnection dir = mockDirectory();
        CreateAttributeProfileRequest req = new CreateAttributeProfileRequest(
                "*", "Default", true, List.of());

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.existsByDirectoryIdAndBranchDn(dirId, "*")).thenReturn(false);
        when(profileRepo.findByDirectoryIdAndIsDefaultTrue(dirId)).thenReturn(Optional.empty());
        when(profileRepo.save(any(AttributeProfile.class)))
                .thenAnswer(inv -> {
                    AttributeProfile p = inv.getArgument(0);
                    p.setId(profileId);
                    return p;
                });

        AttributeProfileDto dto = service.create(dirId, req, adminPrincipal);

        assertThat(dto.isDefault()).isTrue();
        ArgumentCaptor<AttributeProfile> captor = ArgumentCaptor.forClass(AttributeProfile.class);
        verify(profileRepo).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    void create_duplicateBranchDn_throwsConflict() {
        DirectoryConnection dir = mockDirectory();
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.existsByDirectoryIdAndBranchDn(dirId, "ou=people,dc=example,dc=com"))
                .thenReturn(true);

        CreateAttributeProfileRequest req = new CreateAttributeProfileRequest(
                "ou=people,dc=example,dc=com", null, false, List.of());

        assertThatThrownBy(() -> service.create(dirId, req, adminPrincipal))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ou=people,dc=example,dc=com");
    }

    @Test
    void create_secondDefaultProfile_throwsConflict() {
        DirectoryConnection dir = mockDirectory();
        AttributeProfile existing = mockProfile(dir, "*", true);
        existing.setId(UUID.randomUUID()); // different from profileId

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.existsByDirectoryIdAndBranchDn(dirId, "ou=admin,dc=example,dc=com"))
                .thenReturn(false);
        when(profileRepo.findByDirectoryIdAndIsDefaultTrue(dirId))
                .thenReturn(Optional.of(existing));

        CreateAttributeProfileRequest req = new CreateAttributeProfileRequest(
                "ou=admin,dc=example,dc=com", null, true, List.of());

        assertThatThrownBy(() -> service.create(dirId, req, adminPrincipal))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("default attribute profile");
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_replacesEntriesAndUpdatesFields() {
        DirectoryConnection dir = mockDirectory();
        AttributeProfile profile = mockProfile(dir, "ou=people,dc=example,dc=com", false);

        CreateAttributeProfileRequest req = new CreateAttributeProfileRequest(
                "ou=people,dc=example,dc=com",
                "Updated People",
                false,
                List.of(entryReq("sn", 0)));

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(profile));
        when(profileRepo.save(any(AttributeProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepo.save(any(AttributeProfileEntry.class)))
                .thenAnswer(inv -> {
                    AttributeProfileEntry e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        AttributeProfileDto dto = service.update(dirId, profileId, req, adminPrincipal);

        assertThat(dto.displayName()).isEqualTo("Updated People");
        assertThat(dto.entries()).hasSize(1);
        assertThat(dto.entries().get(0).attributeName()).isEqualTo("sn");
        verify(entryRepo).deleteAllByProfileId(profileId);
    }

    @Test
    void update_renameToDuplicateBranchDn_throwsConflict() {
        DirectoryConnection dir = mockDirectory();
        AttributeProfile profile = mockProfile(dir, "ou=people,dc=example,dc=com", false);

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(profile));
        when(profileRepo.existsByDirectoryIdAndBranchDn(dirId, "ou=admin,dc=example,dc=com"))
                .thenReturn(true);

        CreateAttributeProfileRequest req = new CreateAttributeProfileRequest(
                "ou=admin,dc=example,dc=com", null, false, List.of());

        assertThatThrownBy(() -> service.update(dirId, profileId, req, adminPrincipal))
                .isInstanceOf(ConflictException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removesEntriesAndProfile() {
        DirectoryConnection dir = mockDirectory();
        AttributeProfile profile = mockProfile(dir, "ou=people,dc=example,dc=com", false);

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(profile));

        service.delete(dirId, profileId, adminPrincipal);

        verify(entryRepo).deleteAllByProfileId(profileId);
        verify(profileRepo).deleteById(profileId);
    }

    @Test
    void delete_profileNotFound_throwsNotFound() {
        DirectoryConnection dir = mockDirectory();

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.findById(profileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(dirId, profileId, adminPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── entry input type defaulting ───────────────────────────────────────────

    @Test
    void create_nullInputType_defaultsToText() {
        DirectoryConnection dir = mockDirectory();
        UpsertAttributeProfileEntryRequest entryWithNullType =
                new UpsertAttributeProfileEntryRequest(
                        "uid", null, true, true, null, 0, true);
        CreateAttributeProfileRequest req = new CreateAttributeProfileRequest(
                "ou=people,dc=example,dc=com", null, false, List.of(entryWithNullType));

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(profileRepo.existsByDirectoryIdAndBranchDn(any(), any())).thenReturn(false);
        when(profileRepo.save(any())).thenAnswer(inv -> {
            AttributeProfile p = inv.getArgument(0);
            p.setId(profileId);
            return p;
        });
        when(entryRepo.save(any(AttributeProfileEntry.class))).thenAnswer(inv -> {
            AttributeProfileEntry e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        service.create(dirId, req, adminPrincipal);

        ArgumentCaptor<AttributeProfileEntry> captor =
                ArgumentCaptor.forClass(AttributeProfileEntry.class);
        verify(entryRepo).save(captor.capture());
        assertThat(captor.getValue().getInputType()).isEqualTo(InputType.TEXT);
    }
}
