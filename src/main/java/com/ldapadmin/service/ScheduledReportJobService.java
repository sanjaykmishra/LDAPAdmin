package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.report.CreateScheduledReportJobRequest;
import com.ldapadmin.dto.report.ScheduledReportJobDto;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.ScheduledReportJob;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.ScheduledReportJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * CRUD for scheduled report job definitions (§9.2).
 *
 * <p>Jobs are scoped per directory.  This service only manages job
 * <em>definitions</em>; actual report execution is handled by
 * {@link ReportExecutionService}.</p>
 */
@Service
@RequiredArgsConstructor
public class ScheduledReportJobService {

    private final ScheduledReportJobRepository  jobRepo;
    private final DirectoryConnectionRepository dirRepo;
    private final AccountRepository             accountRepo;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScheduledReportJobDto> listByDirectory(UUID directoryId,
                                                        AuthPrincipal principal,
                                                        Pageable pageable) {
        loadDirectory(directoryId);
        return jobRepo.findAllByDirectoryId(directoryId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ScheduledReportJobDto getById(UUID directoryId, UUID jobId,
                                          AuthPrincipal principal) {
        loadDirectory(directoryId);
        return toDto(findJob(jobId, directoryId));
    }

    @Transactional
    public ScheduledReportJobDto create(UUID directoryId,
                                         CreateScheduledReportJobRequest req,
                                         AuthPrincipal principal) {
        DirectoryConnection dir = loadDirectory(directoryId);
        Account creator = principal.id() != null
                ? accountRepo.findById(principal.id()).orElse(null)
                : null;

        ScheduledReportJob job = new ScheduledReportJob();
        job.setDirectory(dir);
        job.setCreatedByAdmin(creator);
        applyRequest(job, req);

        return toDto(jobRepo.save(job));
    }

    @Transactional
    public ScheduledReportJobDto update(UUID directoryId, UUID jobId,
                                         CreateScheduledReportJobRequest req,
                                         AuthPrincipal principal) {
        loadDirectory(directoryId);
        ScheduledReportJob job = findJob(jobId, directoryId);
        applyRequest(job, req);
        return toDto(jobRepo.save(job));
    }

    @Transactional
    public void delete(UUID directoryId, UUID jobId, AuthPrincipal principal) {
        loadDirectory(directoryId);
        findJob(jobId, directoryId); // validates the job belongs to this directory
        jobRepo.deleteById(jobId);
    }

    @Transactional
    public ScheduledReportJobDto setEnabled(UUID directoryId, UUID jobId,
                                             boolean enabled, AuthPrincipal principal) {
        loadDirectory(directoryId);
        ScheduledReportJob job = findJob(jobId, directoryId);
        job.setEnabled(enabled);
        return toDto(jobRepo.save(job));
    }

    // ── Package-visible: used by ReportExecutionService to update last-run ────

    @Transactional
    void recordRunResult(UUID jobId, String status, String message) {
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setLastRunAt(java.time.OffsetDateTime.now());
            job.setLastRunStatus(status);
            job.setLastRunMessage(message);
            jobRepo.save(job);
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private DirectoryConnection loadDirectory(UUID directoryId) {
        return dirRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DirectoryConnection", directoryId));
    }

    private ScheduledReportJob findJob(UUID jobId, UUID directoryId) {
        ScheduledReportJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ScheduledReportJob", jobId));
        if (!job.getDirectory().getId().equals(directoryId)) {
            throw new ResourceNotFoundException("ScheduledReportJob", jobId);
        }
        return job;
    }

    private void applyRequest(ScheduledReportJob job, CreateScheduledReportJobRequest req) {
        job.setName(req.name());
        job.setReportType(req.reportType());
        job.setReportParams(req.reportParams());
        job.setCronExpression(req.cronExpression());
        job.setOutputFormat(req.outputFormat());
        job.setDeliveryMethod(req.deliveryMethod());
        job.setDeliveryRecipients(req.deliveryRecipients());
        job.setS3KeyPrefix(req.s3KeyPrefix());
        job.setEnabled(req.enabled());
    }

    private ScheduledReportJobDto toDto(ScheduledReportJob j) {
        return new ScheduledReportJobDto(
                j.getId(),
                j.getDirectory().getId(),
                j.getName(),
                j.getReportType(),
                j.getReportParams(),
                j.getCronExpression(),
                j.getOutputFormat(),
                j.getDeliveryMethod(),
                j.getDeliveryRecipients(),
                j.getS3KeyPrefix(),
                j.isEnabled(),
                j.getLastRunAt(),
                j.getLastRunStatus(),
                j.getLastRunMessage(),
                j.getCreatedByAdmin() != null ? j.getCreatedByAdmin().getId() : null,
                j.getCreatedAt(),
                j.getUpdatedAt());
    }
}
