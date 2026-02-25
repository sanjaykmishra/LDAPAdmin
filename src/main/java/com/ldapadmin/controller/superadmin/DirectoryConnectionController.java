package com.ldapadmin.controller.superadmin;

import com.ldapadmin.dto.directory.DirectoryConnectionRequest;
import com.ldapadmin.dto.directory.DirectoryConnectionResponse;
import com.ldapadmin.dto.directory.TestConnectionRequest;
import com.ldapadmin.dto.directory.TestConnectionResult;
import com.ldapadmin.service.DirectoryConnectionService;
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
 * Directory connection management for a tenant.
 *
 * <pre>
 *   GET    /api/superadmin/tenants/{tid}/directories          — list
 *   POST   /api/superadmin/tenants/{tid}/directories          — create
 *   GET    /api/superadmin/tenants/{tid}/directories/{id}     — get
 *   PUT    /api/superadmin/tenants/{tid}/directories/{id}     — update
 *   DELETE /api/superadmin/tenants/{tid}/directories/{id}     — delete
 *   POST   /api/superadmin/tenants/{tid}/directories/{id}/evict-pool — evict pool
 *   POST   /api/superadmin/tenants/{tid}/directories/test     — test (not persisted)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/superadmin/tenants/{tenantId}/directories")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class DirectoryConnectionController {

    private final DirectoryConnectionService service;

    @GetMapping
    public List<DirectoryConnectionResponse> list(@PathVariable UUID tenantId) {
        return service.listDirectories(tenantId);
    }

    @PostMapping
    public ResponseEntity<DirectoryConnectionResponse> create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody DirectoryConnectionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createDirectory(tenantId, req));
    }

    @GetMapping("/{id}")
    public DirectoryConnectionResponse get(@PathVariable UUID tenantId,
                                           @PathVariable UUID id) {
        return service.getDirectory(tenantId, id);
    }

    @PutMapping("/{id}")
    public DirectoryConnectionResponse update(@PathVariable UUID tenantId,
                                              @PathVariable UUID id,
                                              @Valid @RequestBody DirectoryConnectionRequest req) {
        return service.updateDirectory(tenantId, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID tenantId,
                                       @PathVariable UUID id) {
        service.deleteDirectory(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/evict-pool")
    public ResponseEntity<Void> evictPool(@PathVariable UUID tenantId,
                                          @PathVariable UUID id) {
        service.evictPool(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public TestConnectionResult test(@Valid @RequestBody TestConnectionRequest req) {
        return service.testConnection(req);
    }
}
