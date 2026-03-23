package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.playbook.*;
import com.ldapadmin.service.LifecyclePlaybookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LifecyclePlaybookController {

    private final LifecyclePlaybookService service;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/directories/{directoryId}/playbooks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public List<PlaybookResponse> list(@PathVariable UUID directoryId) {
        return service.list(directoryId);
    }

    @GetMapping("/api/v1/directories/{directoryId}/playbooks/enabled")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public List<PlaybookResponse> listEnabled(@PathVariable UUID directoryId) {
        return service.listEnabled(directoryId);
    }

    @PostMapping("/api/v1/directories/{directoryId}/playbooks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<PlaybookResponse> create(@PathVariable UUID directoryId,
                                                    @Valid @RequestBody CreatePlaybookRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(directoryId, req));
    }

    @GetMapping("/api/v1/directories/{directoryId}/playbooks/{playbookId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public PlaybookResponse get(@PathVariable UUID directoryId,
                                 @PathVariable UUID playbookId) {
        return service.get(directoryId, playbookId);
    }

    @PutMapping("/api/v1/directories/{directoryId}/playbooks/{playbookId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public PlaybookResponse update(@PathVariable UUID directoryId,
                                    @PathVariable UUID playbookId,
                                    @Valid @RequestBody UpdatePlaybookRequest req) {
        return service.update(directoryId, playbookId, req);
    }

    @DeleteMapping("/api/v1/directories/{directoryId}/playbooks/{playbookId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID directoryId,
                                        @PathVariable UUID playbookId) {
        service.delete(directoryId, playbookId);
        return ResponseEntity.noContent().build();
    }

    // ── Preview & Execute ─────────────────────────────────────────────────────

    @PostMapping("/api/v1/directories/{directoryId}/playbooks/{playbookId}/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public PlaybookPreviewResponse preview(@PathVariable UUID directoryId,
                                            @PathVariable UUID playbookId,
                                            @RequestParam String dn) {
        return service.preview(directoryId, playbookId, dn);
    }

    @PostMapping("/api/v1/directories/{directoryId}/playbooks/{playbookId}/execute")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public List<PlaybookExecutionResponse> execute(@PathVariable UUID directoryId,
                                                    @PathVariable UUID playbookId,
                                                    @Valid @RequestBody ExecutePlaybookRequest req,
                                                    @AuthenticationPrincipal AuthPrincipal principal) {
        return req.targetDns().stream()
                .map(dn -> service.execute(directoryId, playbookId, dn, principal))
                .toList();
    }

    // ── Rollback ──────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/directories/{directoryId}/playbooks/executions/{executionId}/rollback")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public PlaybookExecutionResponse rollback(@PathVariable UUID directoryId,
                                               @PathVariable UUID executionId,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        return service.rollback(executionId, principal);
    }

    // ── History ───────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/directories/{directoryId}/playbooks/{playbookId}/executions")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public List<PlaybookExecutionResponse> listExecutions(@PathVariable UUID directoryId,
                                                           @PathVariable UUID playbookId) {
        return service.listExecutions(directoryId, playbookId);
    }
}
