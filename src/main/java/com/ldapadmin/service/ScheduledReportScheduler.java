package com.ldapadmin.service;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.ScheduledReportJob;
import com.ldapadmin.entity.enums.DeliveryMethod;
import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.repository.ScheduledReportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Polls enabled {@link ScheduledReportJob} records and executes them when
 * their cron expression indicates they are due.
 *
 * <p>Follows the same pattern as {@link com.ldapadmin.service.hr.HrSyncScheduler}:
 * a fixed-delay poll evaluates each job's cron expression against its
 * {@code lastRunAt} timestamp.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledReportScheduler {

    private static final DateTimeFormatter FILE_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final ScheduledReportJobRepository jobRepo;
    private final ScheduledReportJobService    jobService;
    private final ReportExecutionService       reportExecService;
    private final ApprovalNotificationService  notificationService;
    private final S3UploadService              s3UploadService;

    @Scheduled(fixedDelayString = "${ldapadmin.report.poll-interval-ms:60000}",
               initialDelayString = "${ldapadmin.report.poll-initial-delay-ms:30000}")
    public void pollReportJobs() {
        List<ScheduledReportJob> jobs = jobRepo.findAllByEnabledTrue();
        for (ScheduledReportJob job : jobs) {
            try {
                if (isDue(job)) {
                    log.info("Executing scheduled report job '{}' ({})",
                            job.getName(), job.getId());
                    executeJob(job);
                }
            } catch (Exception e) {
                log.error("Error executing scheduled report job '{}' ({}): {}",
                        job.getName(), job.getId(), e.getMessage());
                jobService.recordRunResult(job.getId(), "FAILURE", e.getMessage());
            }
        }
    }

    /**
     * Determines whether a job is due for execution by evaluating its cron
     * expression against the last run timestamp.
     */
    public boolean isDue(ScheduledReportJob job) {
        try {
            CronExpression cron = CronExpression.parse(job.getCronExpression());
            OffsetDateTime lastRun = job.getLastRunAt();

            if (lastRun == null) return true;

            LocalDateTime lastRunLocal = lastRun.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            LocalDateTime nextExecution = cron.next(lastRunLocal);

            return nextExecution != null && !nextExecution.isAfter(LocalDateTime.now(ZoneOffset.UTC));
        } catch (Exception e) {
            log.warn("Invalid cron expression '{}' for report job '{}' ({}): {}",
                    job.getCronExpression(), job.getName(), job.getId(), e.getMessage());
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void executeJob(ScheduledReportJob job) {
        DirectoryConnection dc = job.getDirectory();

        try {
            byte[] reportData = reportExecService.run(
                    dc, job.getReportType(), job.getReportParams(),
                    job.getOutputFormat(), dc.getId());

            deliver(job, reportData);

            jobService.recordRunResult(job.getId(), "SUCCESS",
                    "Report generated and delivered (" + reportData.length + " bytes)");
        } catch (Exception e) {
            log.error("Failed to execute report job '{}': {}", job.getName(), e.getMessage(), e);
            jobService.recordRunResult(job.getId(), "FAILURE",
                    truncate(e.getMessage(), 2000));
        }
    }

    private void deliver(ScheduledReportJob job, byte[] data) throws Exception {
        String fileName = buildFileName(job);
        String contentType = job.getOutputFormat() == OutputFormat.PDF
                ? "application/pdf" : "text/csv";

        if (job.getDeliveryMethod() == DeliveryMethod.S3) {
            deliverToS3(job, data, fileName, contentType);
        } else {
            deliverByEmail(job, data, fileName);
        }
    }

    private void deliverByEmail(ScheduledReportJob job, byte[] data, String fileName) {
        String recipients = job.getDeliveryRecipients();
        if (recipients == null || recipients.isBlank()) {
            throw new IllegalStateException(
                    "No delivery recipients configured for email delivery on job '" + job.getName() + "'");
        }

        String subject = "[LDAPAdmin] Scheduled Report: " + job.getName();
        String body = String.format(
                "The scheduled report '%s' (%s) has completed.\n\n"
                + "Report type: %s\n"
                + "Directory: %s\n"
                + "Generated: %s\n"
                + "File: %s (%d bytes)\n\n"
                + "The report is attached to this email.",
                job.getName(),
                job.getOutputFormat().name(),
                job.getReportType().name(),
                job.getDirectory().getDisplayName(),
                OffsetDateTime.now().toString(),
                fileName,
                data.length);

        for (String recipient : recipients.split(",")) {
            String trimmed = recipient.trim();
            if (!trimmed.isEmpty()) {
                notificationService.sendGenericEmail(trimmed, subject, body);
            }
        }

        log.info("Delivered report '{}' via email to {}", job.getName(), recipients);
    }

    private void deliverToS3(ScheduledReportJob job, byte[] data,
                              String fileName, String contentType) throws Exception {
        if (!s3UploadService.isConfigured()) {
            throw new IllegalStateException(
                    "S3 storage is not configured — cannot deliver report '" + job.getName() + "'");
        }

        String prefix = job.getS3KeyPrefix();
        if (prefix == null) prefix = "";
        if (!prefix.isEmpty() && !prefix.endsWith("/")) prefix += "/";

        String objectKey = prefix + fileName;
        s3UploadService.upload(objectKey, data, contentType);

        log.info("Delivered report '{}' to S3: {}", job.getName(), objectKey);
    }

    private String buildFileName(ScheduledReportJob job) {
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).format(FILE_DATE_FMT);
        String safeName = job.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        String extension = job.getOutputFormat() == OutputFormat.PDF ? ".pdf" : ".csv";
        return safeName + "_" + timestamp + extension;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
