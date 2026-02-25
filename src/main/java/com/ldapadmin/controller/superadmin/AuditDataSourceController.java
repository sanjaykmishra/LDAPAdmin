package com.ldapadmin.controller.superadmin;

import com.ldapadmin.dto.audit.AuditSourceRequest;
import com.ldapadmin.dto.audit.AuditSourceResponse;
import com.ldapadmin.service.AuditDataSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CRUD management of audit data sources (LDAP changelog reader connections).
 *
 * <pre>
 *   GET    /api/superadmin/tenants/{tid}/audit-sources        — list
 *   POST   /api/superadmin/tenants/{tid}/audit-sources        — create
 *   GET    /api/superadmin/tenants/{tid}/audit-sources/{id}   — get
 *   PUT    /api/superadmin/tenants/{tid}/audit-sources/{id}   — update
 *   DELETE /api/superadmin/tenants/{tid}/audit-sources/{id}   — delete
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/superadmin/tenants/{tenantId}/audit-sources")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class AuditDataSourceController {

    private final AuditDataSourceService service;

    @GetMapping
    public List<AuditSourceResponse> list(@PathVariable UUID tenantId) {
        return service.list(tenantId);
    }

    @PostMapping
    public ResponseEntity<AuditSourceResponse> create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody AuditSourceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(tenantId, req));
    }

    @GetMapping("/{id}")
    public AuditSourceResponse get(@PathVariable UUID tenantId,
                                   @PathVariable UUID id) {
        return service.get(tenantId, id);
    }

    @PutMapping("/{id}")
    public AuditSourceResponse update(@PathVariable UUID tenantId,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody AuditSourceRequest req) {
        return service.update(tenantId, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID tenantId,
                                       @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
