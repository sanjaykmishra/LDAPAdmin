package com.ldapadmin.entity.hr;

import com.ldapadmin.entity.enums.HrSyncStatus;
import com.ldapadmin.entity.enums.HrSyncTrigger;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "hr_sync_runs")
@Getter
@Setter
@NoArgsConstructor
public class HrSyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hr_connection_id", nullable = false)
    private HrConnection hrConnection;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HrSyncStatus status = HrSyncStatus.RUNNING;

    @Column(name = "total_employees")
    private Integer totalEmployees;

    @Column(name = "new_employees")
    private int newEmployees = 0;

    @Column(name = "updated_employees")
    private int updatedEmployees = 0;

    @Column(name = "terminated_count")
    private int terminatedCount = 0;

    @Column(name = "matched_count")
    private int matchedCount = 0;

    @Column(name = "unmatched_count")
    private int unmatchedCount = 0;

    @Column(name = "orphaned_count")
    private int orphanedCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 50)
    private HrSyncTrigger triggeredBy = HrSyncTrigger.SCHEDULED;
}
