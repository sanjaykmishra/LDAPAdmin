package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.report.CreateScheduledReportJobRequest;
import com.ldapadmin.dto.report.ScheduledReportJobDto;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.ScheduledReportJob;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.entity.enums.DeliveryMethod;
import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.entity.enums.ReportType;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AdminAccountRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.ScheduledReportJobRepository;
import com.ldapadmin.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledReportJobServiceTest {

    @Mock private ScheduledReportJobRepository  jobRepo;
    @Mock private DirectoryConnectionRepository dirRepo;
    @Mock private TenantRepository              tenantRepo;
    @Mock private AdminAccountRepository        adminRepo;

    private ScheduledReportJobService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID dirId    = UUID.randomUUID();
    private final UUID jobId    = UUID.randomUUID();

    private AuthPrincipal adminPrincipal;
    private AuthPrincipal superadminPrincipal;

    @BeforeEach
    void setUp() {
        service = new ScheduledReportJobService(jobRepo, dirRepo, tenantRepo, adminRepo);
        adminPrincipal = new AuthPrincipal(
                PrincipalType.ADMIN, UUID.randomUUID(), tenantId, "admin");
        superadminPrincipal = new AuthPrincipal(
                PrincipalType.SUPERADMIN, UUID.randomUUID(), null, "superadmin");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DirectoryConnection mockDirectory() {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(dirId);
        dir.setTenant(tenant);
        return dir;
    }

    private ScheduledReportJob mockJob(DirectoryConnection dir) {
        ScheduledReportJob j = new ScheduledReportJob();
        j.setId(jobId);
        j.setDirectory(dir);
        j.setTenant(dir.getTenant());
        j.setName("Weekly Users Report");
        j.setReportType(ReportType.USERS_IN_BRANCH);
        j.setReportParams(Map.of("branchDn", "ou=people,dc=example,dc=com"));
        j.setCronExpression("0 0 8 * * MON");
        j.setOutputFormat(OutputFormat.CSV);
        j.setDeliveryMethod(DeliveryMethod.EMAIL);
        j.setDeliveryRecipients("admin@example.com");
        j.setEnabled(true);
        return j;
    }

    private CreateScheduledReportJobRequest createReq() {
        return new CreateScheduledReportJobRequest(
                "Weekly Users Report",
                ReportType.USERS_IN_BRANCH,
                Map.of("branchDn", "ou=people,dc=example,dc=com"),
                "0 0 8 * * MON",
                OutputFormat.CSV,
                DeliveryMethod.EMAIL,
                "admin@example.com",
                null,
                true);
    }

    // ── listByDirectory ───────────────────────────────────────────────────────

    @Test
    void listByDirectory_returnsPage() {
        DirectoryConnection dir = mockDirectory();
        ScheduledReportJob job = mockJob(dir);

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(jobRepo.findAllByTenantId(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job)));

        var result = service.listByDirectory(dirId, adminPrincipal, Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Weekly Users Report");
    }

    @Test
    void listByDirectory_unknownDirectory_throwsNotFound() {
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByDirectory(dirId, adminPrincipal, Pageable.unpaged()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listByDirectory_superadmin_usesUnfilteredDirRepo() {
        DirectoryConnection dir = mockDirectory();
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dir));
        when(jobRepo.findAllByTenantId(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listByDirectory(dirId, superadminPrincipal, Pageable.unpaged());

        verify(dirRepo, atLeastOnce()).findById(dirId);
        verify(dirRepo, never()).findByIdAndTenantId(any(), any());
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_returnsDto() {
        DirectoryConnection dir = mockDirectory();
        ScheduledReportJob job = mockJob(dir);

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(jobRepo.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));

        ScheduledReportJobDto dto = service.getById(dirId, jobId, adminPrincipal);

        assertThat(dto.id()).isEqualTo(jobId);
        assertThat(dto.reportType()).isEqualTo(ReportType.USERS_IN_BRANCH);
    }

    @Test
    void getById_notFound_throwsNotFound() {
        DirectoryConnection dir = mockDirectory();
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(jobRepo.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(dirId, jobId, adminPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesJobWithAllFields() {
        DirectoryConnection dir = mockDirectory();
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(dir.getTenant()));
        when(adminRepo.findByIdAndTenantId(any(), eq(tenantId))).thenReturn(Optional.empty());
        when(jobRepo.save(any(ScheduledReportJob.class))).thenAnswer(inv -> {
            ScheduledReportJob j = inv.getArgument(0);
            j.setId(jobId);
            return j;
        });

        ScheduledReportJobDto dto = service.create(dirId, createReq(), adminPrincipal);

        assertThat(dto.id()).isEqualTo(jobId);
        assertThat(dto.name()).isEqualTo("Weekly Users Report");
        assertThat(dto.outputFormat()).isEqualTo(OutputFormat.CSV);
        assertThat(dto.enabled()).isTrue();
        verify(jobRepo).save(any(ScheduledReportJob.class));
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_replacesFields() {
        DirectoryConnection dir = mockDirectory();
        ScheduledReportJob job = mockJob(dir);

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(jobRepo.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateScheduledReportJobRequest updated = new CreateScheduledReportJobRequest(
                "Monthly Report", ReportType.DISABLED_ACCOUNTS, null,
                "0 0 9 1 * *", OutputFormat.CSV, DeliveryMethod.EMAIL,
                "manager@example.com", null, false);

        ScheduledReportJobDto dto = service.update(dirId, jobId, updated, adminPrincipal);

        assertThat(dto.name()).isEqualTo("Monthly Report");
        assertThat(dto.reportType()).isEqualTo(ReportType.DISABLED_ACCOUNTS);
        assertThat(dto.enabled()).isFalse();
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removesJob() {
        DirectoryConnection dir = mockDirectory();
        ScheduledReportJob job = mockJob(dir);

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(jobRepo.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));

        service.delete(dirId, jobId, adminPrincipal);

        verify(jobRepo).deleteById(jobId);
    }

    @Test
    void delete_notFound_throwsNotFound() {
        DirectoryConnection dir = mockDirectory();
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(jobRepo.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(dirId, jobId, adminPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── setEnabled ────────────────────────────────────────────────────────────

    @Test
    void setEnabled_false_disablesJob() {
        DirectoryConnection dir = mockDirectory();
        ScheduledReportJob job = mockJob(dir);

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(jobRepo.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScheduledReportJobDto dto = service.setEnabled(dirId, jobId, false, adminPrincipal);

        assertThat(dto.enabled()).isFalse();
    }

    @Test
    void setEnabled_true_enablesJob() {
        DirectoryConnection dir = mockDirectory();
        ScheduledReportJob job = mockJob(dir);
        job.setEnabled(false);

        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(jobRepo.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScheduledReportJobDto dto = service.setEnabled(dirId, jobId, true, adminPrincipal);

        assertThat(dto.enabled()).isTrue();
    }
}
