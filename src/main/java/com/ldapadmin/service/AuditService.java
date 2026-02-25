package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.entity.AuditEvent;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.AuditSource;
import com.ldapadmin.repository.AuditEventRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Records audit events produced by LDAP write operations.
 *
 * <p>All public methods are {@link Async} so callers (typically
 * {@link LdapOperationService}) are not blocked by the DB write.
 * Each call runs in its own transaction so a recording failure never
 * rolls back the original LDAP operation.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository    auditRepo;
    private final DirectoryConnectionRepository dirRepo;

    // ── Internal-event recording ──────────────────────────────────────────────

    /**
     * Records an audit event asynchronously after a successful write op.
     *
     * @param principal   the acting admin
     * @param directoryId the directory that was modified
     * @param action      what was done
     * @param targetDn    the entry DN that was affected
     * @param detail      optional extra detail (attribute names, values, etc.)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuthPrincipal principal,
                       UUID directoryId,
                       AuditAction action,
                       String targetDn,
                       Map<String, Object> detail) {
        try {
            // Single query: load the directory once and derive both displayName and tenantId
            DirectoryConnection dir = dirRepo.findById(directoryId).orElse(null);
            String dirName  = dir != null ? dir.getDisplayName() : null;
            UUID   tenantId = principal.tenantId() != null
                    ? principal.tenantId()
                    : (dir != null ? dir.getTenant().getId() : null);

            AuditEvent event = AuditEvent.builder()
                    .tenantId(tenantId)
                    .source(AuditSource.INTERNAL)
                    .actorId(principal.id())
                    .actorType(principal.type().name())
                    .actorUsername(principal.username())
                    .directoryId(directoryId)
                    .directoryName(dirName)
                    .action(action)
                    .targetDn(targetDn)
                    .detail(detail)
                    .occurredAt(OffsetDateTime.now())
                    .build();

            auditRepo.save(event);
        } catch (Exception ex) {
            // Never let audit failures bubble up to callers.
            log.error("Failed to record audit event [action={}, dn={}, actor={}]: {}",
                    action, targetDn, principal.username(), ex.getMessage(), ex);
        }
    }

    // ── Changelog-event recording (called from LdapChangelogReader) ───────────

    /**
     * Persists a single changelog-sourced audit event.
     * Called synchronously from within the poller's own transaction.
     *
     * @param tenantId        tenant that owns the {@code AuditDataSource}
     * @param directoryId     target directory (may be {@code null})
     * @param directoryName   denormalised name (may be {@code null})
     * @param targetDn        the entry's DN from the changelog
     * @param changeNumber    the {@code changeNumber} attribute value
     * @param changeDetail    all raw attributes from the changelog entry
     * @param occurredAt      timestamp of the change
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChangelogEvent(UUID tenantId,
                                     UUID directoryId,
                                     String directoryName,
                                     String targetDn,
                                     String changeNumber,
                                     Map<String, Object> changeDetail,
                                     OffsetDateTime occurredAt) {
        try {
            if (auditRepo.existsByDirectoryIdAndChangelogChangeNumber(directoryId, changeNumber)) {
                return;  // already recorded (idempotency guard)
            }

            AuditEvent event = AuditEvent.builder()
                    .tenantId(tenantId)
                    .source(AuditSource.LDAP_CHANGELOG)
                    .directoryId(directoryId)
                    .directoryName(directoryName)
                    .action(AuditAction.LDAP_CHANGE)
                    .targetDn(targetDn)
                    .detail(changeDetail)
                    .changelogChangeNumber(changeNumber)
                    .occurredAt(occurredAt)
                    .build();

            auditRepo.save(event);
        } catch (Exception ex) {
            log.error("Failed to record changelog event [changeNumber={}, dn={}]: {}",
                    changeNumber, targetDn, ex.getMessage(), ex);
        }
    }

    // ── Read helpers (used by changelog reader) ───────────────────────────────

    @Transactional(readOnly = true)
    public boolean isChangelogEventRecorded(UUID directoryId, String changeNumber) {
        return auditRepo.existsByDirectoryIdAndChangelogChangeNumber(directoryId, changeNumber);
    }

}
