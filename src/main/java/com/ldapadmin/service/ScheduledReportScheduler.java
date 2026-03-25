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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls enabled {@link ScheduledReportJob} records and executes them when
 * their cron expression indicates they are due.
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

    /** Tracks jobs currently executing to prevent concurrent runs of the same job. */
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${ldapadmin.report.poll-interval-ms:60000}",
               initialDelayString = "${ldapadmin.report.poll-initial-delay-ms:30000}")
    public void pollReportJobs() {
        List<ScheduledReportJob> jobs = jobRepo.findAllByEnabledTrue();
        for (ScheduledReportJob job : jobs) {
            if (inProgress.contains(job.getId())) {
                log.debug("Skipping report job '{}' ({}): already in progress", job.getName(), job.getId());
                continue;
            }
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
     * Executes a specific job immediately (used by "run now" endpoint).
     */
    public void executeJobNow(ScheduledReportJob job) {
        executeJob(job);
    }

    /**
     * Determines whether a job is due for execution by evaluating its cron
     * expression against the last run timestamp, using the job's configured timezone.
     */
    public boolean isDue(ScheduledReportJob job) {
        try {
            CronExpression cron = CronExpression.parse(job.getCronExpression());
            OffsetDateTime lastRun = job.getLastRunAt();

            if (lastRun == null) return true;

            ZoneId zone = job.getTimezone() != null && !job.getTimezone().isBlank()
                    ? ZoneId.of(job.getTimezone()) : ZoneOffset.UTC;

            LocalDateTime lastRunLocal = lastRun.atZoneSameInstant(zone).toLocalDateTime();
            LocalDateTime nextExecution = cron.next(lastRunLocal);
            LocalDateTime now = LocalDateTime.now(zone);

            return nextExecution != null && !nextExecution.isAfter(now);
        } catch (Exception e) {
            log.warn("Invalid cron expression '{}' for report job '{}' ({}): {}",
                    job.getCronExpression(), job.getName(), job.getId(), e.getMessage());
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void executeJob(ScheduledReportJob job) {
        if (!inProgress.add(job.getId())) {
            log.warn("Report job '{}' is already running — skipping", job.getName());
            return;
        }

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
        } finally {
            inProgress.remove(job.getId());
        }
    }

    private void deliver(ScheduledReportJob job, byte[] data) throws Exception {
        String fileName = buildFileName(job);
        String contentType = job.getOutputFormat() == OutputFormat.PDF
                ? "application/pdf" : "text/csv";

        if (job.getDeliveryMethod() == DeliveryMethod.S3) {
            deliverToS3(job, data, fileName, contentType);
        } else {
            deliverByEmail(job, data, fileName, contentType);
        }
    }

    private void deliverByEmail(ScheduledReportJob job, byte[] data,
                                 String fileName, String contentType) {
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
                + "File: %s (%d bytes)",
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
                notificationService.sendEmailWithAttachment(trimmed, subject, body,
                        fileName, contentType, data);
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
