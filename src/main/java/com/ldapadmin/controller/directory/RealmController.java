package com.ldapadmin.controller.directory;

import com.ldapadmin.dto.realm.RealmRequest;
import com.ldapadmin.dto.realm.RealmResponse;
import com.ldapadmin.service.RealmService;
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
 * Realm CRUD for a given directory.
 *
 * <pre>
 *   GET    /api/v1/directories/{directoryId}/realms              — list
 *   POST   /api/v1/directories/{directoryId}/realms              — create
 *   GET    /api/v1/directories/{directoryId}/realms/{realmId}    — get
 *   PUT    /api/v1/directories/{directoryId}/realms/{realmId}    — update
 *   DELETE /api/v1/directories/{directoryId}/realms/{realmId}    — delete
 * </pre>
 *
 * <p>Realm management is restricted to superadmins; realm read access is
 * available to any authenticated user (needed for permission assignment UI).</p>
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}/realms")
@RequiredArgsConstructor
public class RealmController {

    private final RealmService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public List<RealmResponse> list(@PathVariable UUID directoryId) {
        return service.listByDirectory(directoryId);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<RealmResponse> create(
            @PathVariable UUID directoryId,
            @Valid @RequestBody RealmRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(directoryId, req));
    }

    @GetMapping("/{realmId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public RealmResponse get(
            @PathVariable UUID directoryId,
            @PathVariable UUID realmId) {
        return service.get(directoryId, realmId);
    }

    @PutMapping("/{realmId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public RealmResponse update(
            @PathVariable UUID directoryId,
            @PathVariable UUID realmId,
            @Valid @RequestBody RealmRequest req) {
        return service.update(directoryId, realmId, req);
    }

    @DeleteMapping("/{realmId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID directoryId,
            @PathVariable UUID realmId) {
        service.delete(directoryId, realmId);
        return ResponseEntity.noContent().build();
    }
}
