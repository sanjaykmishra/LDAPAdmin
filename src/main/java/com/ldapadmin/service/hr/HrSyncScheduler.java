package com.ldapadmin.service.hr;

import com.ldapadmin.entity.hr.HrConnection;
import com.ldapadmin.entity.enums.HrSyncTrigger;
import com.ldapadmin.repository.hr.HrConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class HrSyncScheduler {

    private final HrConnectionRepository connectionRepo;
    private final HrSyncService syncService;

    /** Tracks connections currently syncing to prevent concurrent runs. */
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${ldapadmin.hr.poll-interval-ms:60000}")
    public void pollHrConnections() {
        List<HrConnection> connections = connectionRepo.findByEnabledTrue();
        for (HrConnection connection : connections) {
            if (inProgress.contains(connection.getId())) {
                log.debug("Skipping HR sync for connection {} — already in progress", connection.getId());
                continue;
            }
            try {
                if (isDue(connection)) {
                    inProgress.add(connection.getId());
                    try {
                        log.info("Triggering scheduled HR sync for connection {}", connection.getId());
                        syncService.sync(connection, HrSyncTrigger.SCHEDULED, null);
                    } finally {
                        inProgress.remove(connection.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Error checking/running HR sync for connection {}: {}",
                        connection.getId(), e.getMessage());
            }
        }
    }

    public boolean isDue(HrConnection connection) {
        try {
            CronExpression cron = CronExpression.parse(connection.getSyncCron());
            OffsetDateTime lastSync = connection.getLastSyncAt();

            if (lastSync == null) return true;

            LocalDateTime lastSyncLocal = lastSync.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            LocalDateTime nextExecution = cron.next(lastSyncLocal);

            return nextExecution != null && !nextExecution.isAfter(LocalDateTime.now(ZoneOffset.UTC));
        } catch (Exception e) {
            log.warn("Invalid cron expression '{}' for connection {}: {}",
                    connection.getSyncCron(), connection.getId(), e.getMessage());
            return false;
        }
    }
}
