package com.ldapadmin.service;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.ScheduledReportJob;
import com.ldapadmin.entity.enums.DeliveryMethod;
import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.entity.enums.ReportType;
import com.ldapadmin.repository.ScheduledReportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledReportSchedulerTest {

    @Mock private ScheduledReportJobRepository jobRepo;
    @Mock private ScheduledReportJobService jobService;
    @Mock private ReportExecutionService reportExecService;
    @Mock private ApprovalNotificationService notificationService;
    @Mock private S3UploadService s3UploadService;

    private ScheduledReportScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ScheduledReportScheduler(
                jobRepo, jobService, reportExecService, notificationService, s3UploadService);
    }

    // ── isDue tests ──────────────────────────────────────────────────────────

    @Test
    void isDue_neverRun_returnsTrue() {
        ScheduledReportJob job = buildJob();
        job.setLastRunAt(null);

        assertThat(scheduler.isDue(job)).isTrue();
    }

    @Test
    void isDue_ranLongAgo_returnsTrue() {
        ScheduledReportJob job = buildJob();
        job.setCronExpression("0 * * * * *"); // every minute
        job.setLastRunAt(OffsetDateTime.now().minusHours(1));

        assertThat(scheduler.isDue(job)).isTrue();
    }

    @Test
    void isDue_ranJustNow_returnsFalse() {
        ScheduledReportJob job = buildJob();
        job.setCronExpression("0 0 * * * *"); // every hour
        job.setLastRunAt(OffsetDateTime.now());

        assertThat(scheduler.isDue(job)).isFalse();
    }

    @Test
    void isDue_invalidCron_returnsFalse() {
        ScheduledReportJob job = buildJob();
        job.setCronExpression("not-a-cron");
        job.setLastRunAt(OffsetDateTime.now().minusDays(1));

        assertThat(scheduler.isDue(job)).isFalse();
    }

    // ── pollReportJobs tests ─────────────────────────────────────────────────

    @Test
    void pollReportJobs_executesDueJobs_emailDelivery() throws Exception {
        ScheduledReportJob job = buildJob();
        job.setLastRunAt(null); // never run = due
        job.setDeliveryMethod(DeliveryMethod.EMAIL);
        job.setDeliveryRecipients("admin@example.com");
        when(jobRepo.findAllByEnabledTrue()).thenReturn(List.of(job));
        when(reportExecService.run(any(), any(), any(), any(), any()))
                .thenReturn("col1,col2\nval1,val2".getBytes());

        scheduler.pollReportJobs();

        verify(reportExecService).run(eq(job.getDirectory()), eq(ReportType.USERS_IN_GROUP),
                eq(job.getReportParams()), eq(OutputFormat.CSV), eq(job.getDirectory().getId()));
        verify(notificationService).sendGenericEmail(eq("admin@example.com"), contains("Scheduled Report"), anyString());
        verify(jobService).recordRunResult(eq(job.getId()), eq("SUCCESS"), anyString());
    }

    @Test
    void pollReportJobs_executesDueJobs_s3Delivery() throws Exception {
        ScheduledReportJob job = buildJob();
        job.setLastRunAt(null);
        job.setDeliveryMethod(DeliveryMethod.S3);
        job.setS3KeyPrefix("reports/daily");
        when(jobRepo.findAllByEnabledTrue()).thenReturn(List.of(job));
        when(reportExecService.run(any(), any(), any(), any(), any()))
                .thenReturn("data".getBytes());
        when(s3UploadService.isConfigured()).thenReturn(true);

        scheduler.pollReportJobs();

        verify(s3UploadService).upload(startsWith("reports/daily/"), any(byte[].class), eq("text/csv"));
        verify(jobService).recordRunResult(eq(job.getId()), eq("SUCCESS"), anyString());
    }

    @Test
    void pollReportJobs_skipsNotDueJobs() {
        ScheduledReportJob job = buildJob();
        job.setCronExpression("0 0 * * * *"); // every hour
        job.setLastRunAt(OffsetDateTime.now()); // just ran
        when(jobRepo.findAllByEnabledTrue()).thenReturn(List.of(job));

        scheduler.pollReportJobs();

        verifyNoInteractions(reportExecService);
        verifyNoInteractions(jobService);
    }

    @Test
    void pollReportJobs_recordsFailureOnError() throws Exception {
        ScheduledReportJob job = buildJob();
        job.setLastRunAt(null);
        job.setDeliveryMethod(DeliveryMethod.EMAIL);
        job.setDeliveryRecipients("admin@example.com");
        when(jobRepo.findAllByEnabledTrue()).thenReturn(List.of(job));
        when(reportExecService.run(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("LDAP unreachable"));

        scheduler.pollReportJobs();

        verify(jobService).recordRunResult(eq(job.getId()), eq("FAILURE"), contains("LDAP unreachable"));
    }

    @Test
    void pollReportJobs_s3NotConfigured_recordsFailure() throws Exception {
        ScheduledReportJob job = buildJob();
        job.setLastRunAt(null);
        job.setDeliveryMethod(DeliveryMethod.S3);
        when(jobRepo.findAllByEnabledTrue()).thenReturn(List.of(job));
        when(reportExecService.run(any(), any(), any(), any(), any()))
                .thenReturn("data".getBytes());
        when(s3UploadService.isConfigured()).thenReturn(false);

        scheduler.pollReportJobs();

        verify(jobService).recordRunResult(eq(job.getId()), eq("FAILURE"),
                contains("S3 storage is not configured"));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private ScheduledReportJob buildJob() {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(UUID.randomUUID());
        dir.setDisplayName("Test LDAP");

        ScheduledReportJob job = new ScheduledReportJob();
        job.setId(UUID.randomUUID());
        job.setDirectory(dir);
        job.setName("Test Report");
        job.setReportType(ReportType.USERS_IN_GROUP);
        job.setReportParams(Map.of("groupDn", "cn=admins,dc=example,dc=com"));
        job.setCronExpression("0 0 * * * ?");
        job.setOutputFormat(OutputFormat.CSV);
        job.setDeliveryMethod(DeliveryMethod.EMAIL);
        job.setEnabled(true);
        return job;
    }
}
