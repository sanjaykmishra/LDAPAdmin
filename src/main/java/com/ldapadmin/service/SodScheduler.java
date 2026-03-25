package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Scheduled job that periodically scans all enabled directories for SoD violations
 * and reopens expired exemptions.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SodScheduler {

    private final SodPolicyService sodPolicyService;
    private final DirectoryConnectionRepository directoryRepo;

    private static final AuthPrincipal SYSTEM_PRINCIPAL =
            new AuthPrincipal(PrincipalType.SUPERADMIN, new UUID(0, 0), "system");

    @Scheduled(cron = "${ldapadmin.sod.scan-cron:0 0 3 * * ?}")
    public void scheduledScan() {
        log.info("Starting scheduled SoD scan");

        List<DirectoryConnection> dirs = directoryRepo.findAll().stream()
                .filter(DirectoryConnection::isEnabled)
                .toList();

        for (DirectoryConnection dir : dirs) {
            try {
                var result = sodPolicyService.scanDirectory(dir.getId(), SYSTEM_PRINCIPAL);
                log.info("SoD scan complete for directory '{}': {} policies, {} violations ({} new, {} resolved)",
                        dir.getDisplayName(), result.policiesScanned(), result.violationsFound(),
                        result.newViolations(), result.resolvedViolations());
            } catch (Exception e) {
                log.error("SoD scan failed for directory '{}': {}", dir.getDisplayName(), e.getMessage());
            }
        }

        // Reopen expired exemptions
        try {
            int reopened = sodPolicyService.reopenExpiredExemptions();
            if (reopened > 0) {
                log.info("Reopened {} expired SoD exemptions", reopened);
            }
        } catch (Exception e) {
            log.error("Failed to reopen expired SoD exemptions: {}", e.getMessage());
        }
    }
}
