package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.evidence.EvidencePackageRequest;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.EvidencePackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates and downloads a ZIP evidence package for compliance audits.
 * Rate-limited to one concurrent generation per user.
 */
@RestController
@RequiredArgsConstructor
public class EvidencePackageController {

    private final EvidencePackageService evidencePackageService;

    private final ConcurrentHashMap<UUID, Boolean> activeGenerations = new ConcurrentHashMap<>();

    @PostMapping("/api/v1/directories/{directoryId}/evidence-package")
    @RequiresFeature(FeatureKey.REPORTS_RUN)
    public ResponseEntity<byte[]> generate(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody EvidencePackageRequest request) throws IOException {

        UUID userId = principal.id();

        // Rate limit: one concurrent generation per user
        if (activeGenerations.putIfAbsent(userId, Boolean.TRUE) != null) {
            return ResponseEntity.status(429)
                    .header("Retry-After", "30")
                    .build();
        }

        try {
            byte[] zip = evidencePackageService.generateEvidencePackage(
                    directoryId, request.campaignIds(),
                    request.includeSod(), request.includeEntitlements(),
                    request.includeAuditEvents(),
                    principal.username());

            String filename = "evidence-package-"
                    + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".zip";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/zip"));
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename(filename).build());

            return ResponseEntity.ok().headers(headers).body(zip);
        } finally {
            activeGenerations.remove(userId);
        }
    }
}
