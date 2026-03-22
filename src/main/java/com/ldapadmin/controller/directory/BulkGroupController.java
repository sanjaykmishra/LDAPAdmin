package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.ApiRateLimiter;
import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.csv.BulkImportPreviewResult;
import com.ldapadmin.dto.csv.BulkImportRequest;
import com.ldapadmin.dto.csv.BulkImportResult;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.LdapOperationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Bulk CSV import and export endpoints for LDAP groups.
 *
 * <pre>
 *   POST /api/v1/directories/{directoryId}/groups/import/preview — preview
 *   POST /api/v1/directories/{directoryId}/groups/import         — import
 *   GET  /api/v1/directories/{directoryId}/groups/export         — export
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}/groups")
@RequiredArgsConstructor
public class BulkGroupController {

    private final LdapOperationService service;
    private final ApiRateLimiter       rateLimiter;

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresFeature(FeatureKey.BULK_IMPORT)
    public ResponseEntity<BulkImportPreviewResult> previewImport(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestPart("request") @Valid BulkImportRequest request) throws IOException {

        rateLimiter.check(principal.username(), "bulk-group-import-preview");
        BulkImportPreviewResult result = service.previewBulkGroupImport(
                directoryId, principal, file.getInputStream(), request);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresFeature(FeatureKey.BULK_IMPORT)
    public ResponseEntity<BulkImportResult> importGroups(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestPart("request") @Valid BulkImportRequest request,
            @RequestParam(defaultValue = "member") String memberAttribute,
            @RequestParam(defaultValue = "groupOfNames") String objectClass) throws IOException {

        rateLimiter.check(principal.username(), "bulk-group-import");
        BulkImportResult result = service.bulkImportGroups(
                directoryId, principal, file.getInputStream(), request,
                memberAttribute, objectClass);
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @RequiresFeature(FeatureKey.BULK_EXPORT)
    public ResponseEntity<byte[]> exportGroups(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String baseDn,
            @RequestParam(required = false, defaultValue = "") String attributes,
            @RequestParam(defaultValue = "member") String memberAttribute) throws IOException {

        rateLimiter.check(principal.username(), "bulk-group-export");
        List<String> attrList = attributes.isBlank()
                ? List.of()
                : Arrays.stream(attributes.split(",")).map(String::trim).toList();

        byte[] csv = service.bulkExportGroups(
                directoryId, principal, filter, baseDn, memberAttribute, attrList);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"groups.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
