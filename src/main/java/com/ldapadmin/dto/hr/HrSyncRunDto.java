package com.ldapadmin.dto.hr;

import com.ldapadmin.entity.enums.HrSyncStatus;
import com.ldapadmin.entity.enums.HrSyncTrigger;
import com.ldapadmin.entity.hr.HrSyncRun;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HrSyncRunDto(
        UUID id,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        HrSyncStatus status,
        Integer totalEmployees,
        int newEmployees,
        int updatedEmployees,
        int terminatedCount,
        int matchedCount,
        int unmatchedCount,
        int orphanedCount,
        String errorMessage,
        HrSyncTrigger triggeredBy
) {
    public static HrSyncRunDto from(HrSyncRun r) {
        return new HrSyncRunDto(
                r.getId(),
                r.getStartedAt(),
                r.getCompletedAt(),
                r.getStatus(),
                r.getTotalEmployees(),
                r.getNewEmployees(),
                r.getUpdatedEmployees(),
                r.getTerminatedCount(),
                r.getMatchedCount(),
                r.getUnmatchedCount(),
                r.getOrphanedCount(),
                r.getErrorMessage(),
                r.getTriggeredBy()
        );
    }
}
