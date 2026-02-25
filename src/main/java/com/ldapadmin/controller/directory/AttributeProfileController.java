package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.profile.AttributeProfileDto;
import com.ldapadmin.dto.profile.CreateAttributeProfileRequest;
import com.ldapadmin.service.AttributeProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * CRUD endpoints for attribute profiles scoped to a directory (§5.2).
 *
 * <pre>
 *   GET    /api/directories/{directoryId}/attribute-profiles              — list all
 *   POST   /api/directories/{directoryId}/attribute-profiles              — create
 *   GET    /api/directories/{directoryId}/attribute-profiles/{profileId}  — get one
 *   PUT    /api/directories/{directoryId}/attribute-profiles/{profileId}  — replace
 *   DELETE /api/directories/{directoryId}/attribute-profiles/{profileId}  — delete
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}/attribute-profiles")
@RequiredArgsConstructor
public class AttributeProfileController {

    private final AttributeProfileService service;

    @GetMapping
    public List<AttributeProfileDto> list(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.listByDirectory(directoryId, principal);
    }

    @PostMapping
    public ResponseEntity<AttributeProfileDto> create(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateAttributeProfileRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(directoryId, req, principal));
    }

    @GetMapping("/{profileId}")
    public AttributeProfileDto get(
            @PathVariable UUID directoryId,
            @PathVariable UUID profileId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getById(directoryId, profileId, principal);
    }

    @PutMapping("/{profileId}")
    public AttributeProfileDto update(
            @PathVariable UUID directoryId,
            @PathVariable UUID profileId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateAttributeProfileRequest req) {
        return service.update(directoryId, profileId, req, principal);
    }

    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID directoryId,
            @PathVariable UUID profileId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        service.delete(directoryId, profileId, principal);
        return ResponseEntity.noContent().build();
    }
}
