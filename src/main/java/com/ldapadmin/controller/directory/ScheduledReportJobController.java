package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.ApiRateLimiter;
import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.report.CreateScheduledReportJobRequest;
import com.ldapadmin.dto.report.RunReportRequest;
import com.ldapadmin.dto.report.ScheduledReportJobDto;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.service.ReportExecutionService;
import com.ldapadmin.service.ScheduledReportJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

/**
 * Scheduled report job management and on-demand report execution (§9).
 *
 * <pre>
 *   GET    /api/directories/{directoryId}/report-jobs              — list (paginated)
 *   POST   /api/directories/{directoryId}/report-jobs              — create
 *   GET    /api/directories/{directoryId}/report-jobs/{jobId}      — get
 *   PUT    /api/directories/{directoryId}/report-jobs/{jobId}      — replace
 *   DELETE /api/directories/{directoryId}/report-jobs/{jobId}      — delete
 *   PATCH  /api/directories/{directoryId}/report-jobs/{jobId}/enabled — toggle enabled
 *   POST   /api/directories/{directoryId}/reports/run              — run on-demand → CSV
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}")
@RequiredArgsConstructor
public class ScheduledReportJobController {

    private final ScheduledReportJobService     jobService;
    private final ReportExecutionService        executionService;
    private final DirectoryConnectionRepository dirRepo;
    private final ApiRateLimiter                rateLimiter;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @GetMapping("/report-jobs")
    @RequiresFeature(FeatureKey.REPORTS_SCHEDULE)
    public Page<ScheduledReportJobDto> list(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return jobService.listByDirectory(directoryId, principal, pageable);
    }

    @PostMapping("/report-jobs")
    @RequiresFeature(FeatureKey.REPORTS_SCHEDULE)
    public ResponseEntity<ScheduledReportJobDto> create(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateScheduledReportJobRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.create(directoryId, req, principal));
    }

    @GetMapping("/report-jobs/{jobId}")
    @RequiresFeature(FeatureKey.REPORTS_SCHEDULE)
    public ScheduledReportJobDto get(
            @PathVariable UUID directoryId,
            @PathVariable UUID jobId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return jobService.getById(directoryId, jobId, principal);
    }

    @PutMapping("/report-jobs/{jobId}")
    @RequiresFeature(FeatureKey.REPORTS_SCHEDULE)
    public ScheduledReportJobDto update(
            @PathVariable UUID directoryId,
            @PathVariable UUID jobId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateScheduledReportJobRequest req) {
        return jobService.update(directoryId, jobId, req, principal);
    }

    @DeleteMapping("/report-jobs/{jobId}")
    @RequiresFeature(FeatureKey.REPORTS_SCHEDULE)
    public ResponseEntity<Void> delete(
            @PathVariable UUID directoryId,
            @PathVariable UUID jobId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        jobService.delete(directoryId, jobId, principal);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/report-jobs/{jobId}/enabled")
    @RequiresFeature(FeatureKey.REPORTS_SCHEDULE)
    public ScheduledReportJobDto setEnabled(
            @PathVariable UUID directoryId,
            @PathVariable UUID jobId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam boolean enabled) {
        return jobService.setEnabled(directoryId, jobId, enabled, principal);
    }

    // ── On-demand execution ───────────────────────────────────────────────────

    /**
     * Runs a report immediately and returns the result as a CSV file download.
     */
    @PostMapping("/reports/run")
    @RequiresFeature(FeatureKey.REPORTS_RUN)
    public ResponseEntity<byte[]> run(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody RunReportRequest req) throws IOException {

        rateLimiter.check(principal.username(), "report-run");
        DirectoryConnection dc = dirRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
        OutputFormat format = req.outputFormat() != null ? req.outputFormat() : OutputFormat.CSV;

        byte[] data = executionService.run(dc, req.reportType(), req.reportParams(), format, directoryId);

        String filename = req.reportType().name().toLowerCase() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
}
