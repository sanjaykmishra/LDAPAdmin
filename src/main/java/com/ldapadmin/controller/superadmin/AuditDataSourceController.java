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
 *   GET    /api/v1/superadmin/audit-sources        — list
 *   POST   /api/v1/superadmin/audit-sources        — create
 *   GET    /api/v1/superadmin/audit-sources/{id}   — get
 *   PUT    /api/v1/superadmin/audit-sources/{id}   — update
 *   DELETE /api/v1/superadmin/audit-sources/{id}   — delete
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/superadmin/audit-sources")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class AuditDataSourceController {

    private final AuditDataSourceService service;

    @GetMapping
    public List<AuditSourceResponse> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<AuditSourceResponse> create(@Valid @RequestBody AuditSourceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @GetMapping("/{id}")
    public AuditSourceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public AuditSourceResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody AuditSourceRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
