package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.PdfReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

/**
 * Compliance report PDF generation endpoints.
 *
 * <ul>
 *   <li>User Access Report — group memberships in a directory</li>
 *   <li>Access Review Summary — campaign decision breakdown</li>
 *   <li>Privileged Account Inventory — admin accounts and roles (superadmin only, non-directory-scoped)</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class ComplianceReportController {

    private final PdfReportService pdfReportService;

    /**
     * Generates a PDF showing who has access to what groups in the directory.
     */
    @GetMapping("/api/v1/directories/{directoryId}/compliance-reports/user-access")
    @RequiresFeature(FeatureKey.REPORTS_RUN)
    public ResponseEntity<byte[]> userAccessReport(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String groupDn) throws IOException {

        byte[] pdf = pdfReportService.generateUserAccessReport(directoryId, groupDn);
        return pdfResponse(pdf, "user-access-report.pdf");
    }

    /**
     * Generates a PDF summarizing an access review campaign's decisions.
     */
    @GetMapping("/api/v1/directories/{directoryId}/compliance-reports/access-review-summary/{campaignId}")
    @RequiresFeature(FeatureKey.REPORTS_RUN)
    public ResponseEntity<byte[]> accessReviewSummary(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID campaignId) throws IOException {

        // Verify campaign belongs to this directory
        var campaign = pdfReportService.getCampaignForDirectory(directoryId, campaignId);
        byte[] pdf = pdfReportService.generateAccessReviewSummary(campaignId);
        return pdfResponse(pdf, "access-review-summary.pdf");
    }

    /**
     * Generates a PDF listing all privileged accounts and their roles.
     * Superadmin-only — not directory-scoped.
     */
    @GetMapping("/api/v1/compliance-reports/privileged-accounts")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<byte[]> privilegedAccountInventory(
            @AuthenticationPrincipal AuthPrincipal principal) throws IOException {

        byte[] pdf = pdfReportService.generatePrivilegedAccountInventory();
        return pdfResponse(pdf, "privileged-account-inventory.pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
