package com.ldapportal.entity;

import com.ldapportal.entity.enums.DeliveryMethod;
import com.ldapportal.entity.enums.OutputFormat;
import com.ldapportal.entity.enums.ReportType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Cron-scheduled report job definition (§9.2).
 * <p>
 * {@code reportParams} is a JSONB map whose keys depend on {@code reportType}:
 * <ul>
 *   <li>USERS_IN_GROUP     — {@code {"groupDn": "..."}}</li>
 *   <li>USERS_IN_BRANCH    — {@code {"branchDn": "..."}}</li>
 *   <li>RECENTLY_ADDED etc — {@code {"lookbackDays": 30}}</li>
 * </ul>
 */
@Entity
@Table(name = "scheduled_report_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ScheduledReportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id", nullable = false)
    private DirectoryConnection directory;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    private ReportType reportType;

    /** Report-specific parameters stored as a JSONB object. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_params", columnDefinition = "jsonb")
    private Map<String, Object> reportParams;

    /** Spring/Quartz cron expression (6 or 7 fields). */
    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, length = 10)
    private OutputFormat outputFormat = OutputFormat.CSV;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 10)
    private DeliveryMethod deliveryMethod = DeliveryMethod.EMAIL;

    /** Comma-separated recipient email addresses (used when deliveryMethod = EMAIL). */
    @Column(name = "delivery_recipients", columnDefinition = "TEXT")
    private String deliveryRecipients;

    /** Object-key prefix written to the tenant S3 bucket (used when deliveryMethod = S3). */
    @Column(name = "s3_key_prefix")
    private String s3KeyPrefix;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "last_run_status", length = 50)
    private String lastRunStatus;

    @Column(name = "last_run_message", columnDefinition = "TEXT")
    private String lastRunMessage;

    /** SET NULL if the creating admin is later deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id")
    private AdminAccount createdByAdmin;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
