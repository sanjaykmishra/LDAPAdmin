package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.report.CreateScheduledReportJobRequest;
import com.ldapadmin.dto.report.ScheduledReportJobDto;
import com.ldapadmin.entity.AdminAccount;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.ScheduledReportJob;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AdminAccountRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.ScheduledReportJobRepository;
import com.ldapadmin.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * CRUD for scheduled report job definitions (§9.2).
 *
 * <h3>Tenant isolation</h3>
 * <p>Non-superadmin principals can only access jobs belonging to their own
 * tenant and directories within that tenant.  Superadmins may access any
 * tenant's jobs.</p>
 *
 * <p>This service only manages job <em>definitions</em>; actual report execution
 * is handled by {@link ReportExecutionService}.</p>
 */
@Service
@RequiredArgsConstructor
public class ScheduledReportJobService {

    private final ScheduledReportJobRepository jobRepo;
    private final DirectoryConnectionRepository dirRepo;
    private final TenantRepository             tenantRepo;
    private final AdminAccountRepository       adminRepo;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScheduledReportJobDto> listByDirectory(UUID directoryId,
                                                        AuthPrincipal principal,
                                                        Pageable pageable) {
        loadDirectory(directoryId, principal);
        return jobRepo.findAllByTenantId(resolveTenantId(principal, directoryId), pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ScheduledReportJobDto getById(UUID directoryId, UUID jobId,
                                          AuthPrincipal principal) {
        loadDirectory(directoryId, principal);
        return toDto(findJob(jobId, principal));
    }

    @Transactional
    public ScheduledReportJobDto create(UUID directoryId,
                                         CreateScheduledReportJobRequest req,
                                         AuthPrincipal principal) {
        DirectoryConnection dir = loadDirectory(directoryId, principal);
        Tenant tenant = loadTenant(principal, dir);
        AdminAccount creator = resolveCreator(principal);

        ScheduledReportJob job = new ScheduledReportJob();
        job.setDirectory(dir);
        job.setTenant(tenant);
        job.setCreatedByAdmin(creator);
        applyRequest(job, req);

        return toDto(jobRepo.save(job));
    }

    @Transactional
    public ScheduledReportJobDto update(UUID directoryId, UUID jobId,
                                         CreateScheduledReportJobRequest req,
                                         AuthPrincipal principal) {
        loadDirectory(directoryId, principal);
        ScheduledReportJob job = findJob(jobId, principal);
        applyRequest(job, req);
        return toDto(jobRepo.save(job));
    }

    @Transactional
    public void delete(UUID directoryId, UUID jobId, AuthPrincipal principal) {
        loadDirectory(directoryId, principal);
        findJob(jobId, principal); // validates access
        jobRepo.deleteById(jobId);
    }

    @Transactional
    public ScheduledReportJobDto setEnabled(UUID directoryId, UUID jobId,
                                             boolean enabled, AuthPrincipal principal) {
        loadDirectory(directoryId, principal);
        ScheduledReportJob job = findJob(jobId, principal);
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

    private DirectoryConnection loadDirectory(UUID directoryId, AuthPrincipal principal) {
        if (principal.isSuperadmin()) {
            return dirRepo.findById(directoryId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "DirectoryConnection", directoryId));
        }
        return dirRepo.findByIdAndTenantId(directoryId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DirectoryConnection", directoryId));
    }

    private ScheduledReportJob findJob(UUID jobId, AuthPrincipal principal) {
        if (principal.isSuperadmin()) {
            return jobRepo.findById(jobId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "ScheduledReportJob", jobId));
        }
        return jobRepo.findByIdAndTenantId(jobId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ScheduledReportJob", jobId));
    }

    private UUID resolveTenantId(AuthPrincipal principal, UUID directoryId) {
        if (principal.isSuperadmin()) {
            return dirRepo.findById(directoryId)
                    .map(d -> d.getTenant().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "DirectoryConnection", directoryId));
        }
        return principal.tenantId();
    }

    private Tenant loadTenant(AuthPrincipal principal, DirectoryConnection dir) {
        if (principal.isSuperadmin()) {
            return dir.getTenant();
        }
        return tenantRepo.findById(principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant", principal.tenantId()));
    }

    private AdminAccount resolveCreator(AuthPrincipal principal) {
        if (principal.isSuperadmin() || principal.id() == null) {
            return null;
        }
        return adminRepo.findByIdAndTenantId(principal.id(), principal.tenantId())
                .orElse(null);
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
