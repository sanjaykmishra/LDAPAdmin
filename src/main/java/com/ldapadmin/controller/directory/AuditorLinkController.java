package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.auditor.AuditorLinkDto;
import com.ldapadmin.dto.auditor.CreateAuditorLinkRequest;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.AuditorLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-side CRUD for auditor links (authenticated, directory-scoped).
 * Allows admins to create, list, and revoke shareable evidence portal links.
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}/auditor-links")
@RequiredArgsConstructor
public class AuditorLinkController {

    private final AuditorLinkService auditorLinkService;

    @PostMapping
    @RequiresFeature(FeatureKey.AUDITOR_MANAGE)
    public ResponseEntity<AuditorLinkDto> create(
            @DirectoryId @PathVariable UUID directoryId,
            @Valid @RequestBody CreateAuditorLinkRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {

        AuditorLinkDto dto = auditorLinkService.create(directoryId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    @RequiresFeature(FeatureKey.AUDITOR_MANAGE)
    public List<AuditorLinkDto> list(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {

        return auditorLinkService.list(directoryId);
    }

    @DeleteMapping("/{linkId}")
    @RequiresFeature(FeatureKey.AUDITOR_MANAGE)
    public ResponseEntity<Void> revoke(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID linkId,
            @AuthenticationPrincipal AuthPrincipal principal) {

        auditorLinkService.revoke(linkId, principal);
        return ResponseEntity.noContent().build();
    }
}
