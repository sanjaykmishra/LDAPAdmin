package com.ldapadmin.dto.drift;

import java.util.UUID;

public record DriftAnalysisResult(
        UUID snapshotId,
        int rulesEvaluated,
        int peerGroupsAnalyzed,
        int totalFindings,
        int highFindings,
        int mediumFindings,
        int lowFindings,
        int existingSkipped
) {}
