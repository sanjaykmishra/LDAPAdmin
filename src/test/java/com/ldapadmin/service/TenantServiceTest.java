package com.ldapadmin.service;

import com.ldapadmin.dto.tenant.TenantRequest;
import com.ldapadmin.dto.tenant.TenantResponse;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.TenantAuthConfigRepository;
import com.ldapadmin.repository.TenantRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock private TenantRepository           tenantRepo;
    @Mock private TenantAuthConfigRepository authConfigRepo;
    @Mock private DirectoryConnectionRepository dirRepo;

    private TenantService service;

    @BeforeEach
    void setUp() {
        service = new TenantService(tenantRepo, authConfigRepo, dirRepo);
    }

    @Test
    void listTenants_returnsMappedList() {
        Tenant t = tenantWithId(UUID.randomUUID(), "Acme", "acme");
        when(tenantRepo.findAll()).thenReturn(List.of(t));

        List<TenantResponse> result = service.listTenants();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).slug()).isEqualTo("acme");
    }

    @Test
    void createTenant_success() {
        TenantRequest req = new TenantRequest("Acme Corp", "acme", true);
        when(tenantRepo.existsBySlug("acme")).thenReturn(false);
        when(tenantRepo.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TenantResponse resp = service.createTenant(req);

        assertThat(resp.name()).isEqualTo("Acme Corp");
        assertThat(resp.slug()).isEqualTo("acme");
    }

    @Test
    void createTenant_duplicateSlug_throwsConflict() {
        when(tenantRepo.existsBySlug("acme")).thenReturn(true);

        assertThatThrownBy(() -> service.createTenant(new TenantRequest("X", "acme", true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getTenant_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(tenantRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTenant(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTenant_callsDelete() {
        UUID id = UUID.randomUUID();
        Tenant t = tenantWithId(id, "Acme", "acme");
        when(tenantRepo.findById(id)).thenReturn(Optional.of(t));

        service.deleteTenant(id);

        verify(tenantRepo).delete(t);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Tenant tenantWithId(UUID id, String name, String slug) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setName(name);
        t.setSlug(slug);
        t.setEnabled(true);
        return t;
    }
}
