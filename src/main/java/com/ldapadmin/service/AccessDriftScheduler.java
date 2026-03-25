package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.entity.AccessSnapshot;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.PeerGroupRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Daily scheduled job that captures access snapshots and runs drift analysis
 * for all enabled directories that have peer group rules configured.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccessDriftScheduler {

    private final DirectoryConnectionRepository directoryRepo;
    private final PeerGroupRuleRepository ruleRepo;
    private final AccessSnapshotService snapshotService;
    private final AccessDriftAnalysisService analysisService;

    private static final AuthPrincipal SYSTEM_PRINCIPAL =
            new AuthPrincipal(PrincipalType.SUPERADMIN, new UUID(0, 0), "system");

    @Scheduled(cron = "${ldapadmin.drift.analysis-cron:0 0 4 * * ?}")
    public void scheduledAnalysis() {
        log.info("Starting scheduled access drift analysis");

        List<DirectoryConnection> dirs = directoryRepo.findAll().stream()
                .filter(DirectoryConnection::isEnabled)
                .toList();

        for (DirectoryConnection dir : dirs) {
            // Skip directories with no rules configured
            if (ruleRepo.findByDirectoryIdAndEnabledTrue(dir.getId()).isEmpty()) {
                continue;
            }

            try {
                log.info("Capturing access snapshot for directory '{}'", dir.getDisplayName());
                AccessSnapshot snapshot = snapshotService.captureSnapshot(dir.getId());

                log.info("Running drift analysis for directory '{}'", dir.getDisplayName());
                var result = analysisService.analyze(dir.getId(), snapshot.getId(), SYSTEM_PRINCIPAL);

                log.info("Drift analysis complete for '{}': {} findings ({} high, {} medium, {} low)",
                        dir.getDisplayName(), result.totalFindings(),
                        result.highFindings(), result.mediumFindings(), result.lowFindings());
            } catch (Exception e) {
                log.error("Drift analysis failed for directory '{}': {}",
                        dir.getDisplayName(), e.getMessage(), e);
            }
        }
    }
}
