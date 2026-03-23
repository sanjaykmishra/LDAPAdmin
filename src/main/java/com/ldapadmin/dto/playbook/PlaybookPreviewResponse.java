package com.ldapadmin.dto.playbook;

import java.util.List;

public record PlaybookPreviewResponse(
        String targetDn,
        List<StepPreview> steps) {

    public record StepPreview(
            int stepOrder,
            String action,
            String description,
            boolean reversible,
            boolean continueOnError) {
    }
}
