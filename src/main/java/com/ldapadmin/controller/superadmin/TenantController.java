package com.ldapadmin.controller.superadmin;

import com.ldapadmin.dto.tenant.TenantAuthConfigRequest;
import com.ldapadmin.dto.tenant.TenantAuthConfigResponse;
import com.ldapadmin.dto.tenant.TenantRequest;
import com.ldapadmin.dto.tenant.TenantResponse;
import com.ldapadmin.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant management.
 *
 * <pre>
 *   GET    /api/superadmin/tenants               — list all
 *   POST   /api/superadmin/tenants               — create
 *   GET    /api/superadmin/tenants/{id}          — get
 *   PUT    /api/superadmin/tenants/{id}          — update
 *   DELETE /api/superadmin/tenants/{id}          — delete
 *   GET    /api/superadmin/tenants/{id}/auth-config — get auth config
 *   PUT    /api/superadmin/tenants/{id}/auth-config — upsert auth config
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/superadmin/tenants")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService service;

    @GetMapping
    public List<TenantResponse> list() {
        return service.listTenants();
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody TenantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTenant(req));
    }

    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable UUID id) {
        return service.getTenant(id);
    }

    @PutMapping("/{id}")
    public TenantResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody TenantRequest req) {
        return service.updateTenant(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/auth-config")
    public TenantAuthConfigResponse getAuthConfig(@PathVariable UUID id) {
        return service.getAuthConfig(id);
    }

    @PutMapping("/{id}/auth-config")
    public TenantAuthConfigResponse saveAuthConfig(
            @PathVariable UUID id,
            @Valid @RequestBody TenantAuthConfigRequest req) {
        return service.saveAuthConfig(id, req);
    }
}
