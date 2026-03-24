package com.ldapadmin.service.siem;

import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.entity.AuditEvent;
import com.ldapadmin.service.ApplicationSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronously exports audit events to the configured SIEM target.
 * Called by {@link com.ldapadmin.service.AuditService} after each event is recorded.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SiemExportService {

    private final ApplicationSettingsService settingsService;
    private final SiemFormatter              formatter;
    private final SiemClient                 client;

    /**
     * Exports a single audit event to the configured SIEM destination.
     * Runs async — failures are logged but never propagated.
     */
    @Async
    public void export(AuditEvent event) {
        try {
            ApplicationSettings settings = settingsService.getEntity();
            if (!settings.isSiemEnabled() || settings.getSiemProtocol() == null
                    || settings.getSiemFormat() == null) {
                return;
            }

            String message = formatter.format(event, settings.getSiemFormat());
            client.send(settings, message);
        } catch (Exception e) {
            log.error("SIEM export failed for event [{}]: {}", event.getId(), e.getMessage());
        }
    }

    /**
     * Sends a test event to verify SIEM connectivity.
     */
    public String sendTestEvent() {
        ApplicationSettings settings = settingsService.getEntity();
        if (!settings.isSiemEnabled()) {
            return "SIEM export is not enabled.";
        }
        if (settings.getSiemProtocol() == null || settings.getSiemFormat() == null) {
            return "SIEM protocol or format is not configured.";
        }

        // Build a synthetic test event
        AuditEvent testEvent = AuditEvent.builder()
                .id(java.util.UUID.randomUUID())
                .source(com.ldapadmin.entity.enums.AuditSource.INTERNAL)
                .action(com.ldapadmin.entity.enums.AuditAction.LDAP_CHANGE)
                .actorUsername("system")
                .actorType("SUPERADMIN")
                .targetDn("cn=test,dc=example,dc=com")
                .occurredAt(java.time.OffsetDateTime.now())
                .build();

        try {
            String message = formatter.format(testEvent, settings.getSiemFormat());
            client.send(settings, message);
            return "Test event sent successfully.";
        } catch (Exception e) {
            return "Test event failed: " + e.getMessage();
        }
    }

    /**
     * Tests network connectivity to the configured SIEM target without sending events.
     */
    public String testConnectivity() {
        ApplicationSettings settings = settingsService.getEntity();
        return client.testConnectivity(settings);
    }
}
