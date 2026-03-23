package com.ldapadmin.dto.playbook;

import com.ldapadmin.entity.LifecyclePlaybook;
import com.ldapadmin.entity.PlaybookStep;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PlaybookResponse(
        UUID id,
        UUID directoryId,
        String name,
        String description,
        String type,
        UUID profileId,
        String profileName,
        boolean requireApproval,
        boolean enabled,
        List<StepEntry> steps,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record StepEntry(
            UUID id,
            int stepOrder,
            String action,
            String parameters,
            boolean continueOnError) {

        public static StepEntry from(PlaybookStep s) {
            return new StepEntry(
                    s.getId(), s.getStepOrder(), s.getAction().name(),
                    s.getParameters(), s.isContinueOnError());
        }
    }

    public static PlaybookResponse from(LifecyclePlaybook p, List<PlaybookStep> steps) {
        return new PlaybookResponse(
                p.getId(),
                p.getDirectory().getId(),
                p.getName(),
                p.getDescription(),
                p.getType().name(),
                p.getProfile() != null ? p.getProfile().getId() : null,
                p.getProfile() != null ? p.getProfile().getName() : null,
                p.isRequireApproval(),
                p.isEnabled(),
                steps.stream().map(StepEntry::from).toList(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
