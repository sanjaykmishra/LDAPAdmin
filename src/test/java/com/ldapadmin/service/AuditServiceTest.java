package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.entity.AuditEvent;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.AuditSource;
import com.ldapadmin.repository.AuditEventRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditEventRepository        auditRepo;
    @Mock private DirectoryConnectionRepository dirRepo;

    private AuditService auditService;

    private final UUID tenantId     = UUID.randomUUID();
    private final UUID adminId      = UUID.randomUUID();
    private final UUID directoryId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditRepo, dirRepo);
    }

    // ── Internal event recording ──────────────────────────────────────────────

    @Test
    void record_savesInternalEvent_forAdminPrincipal() {
        AuthPrincipal principal = new AuthPrincipal(
                PrincipalType.ADMIN, adminId, tenantId, "alice");

        DirectoryConnection dc = mockDirectory("corp-ldap");
        when(dirRepo.findById(directoryId)).thenReturn(Optional.of(dc));

        auditService.record(principal, directoryId, AuditAction.USER_CREATE,
                "uid=alice,ou=users,dc=corp,dc=com",
                Map.of("attributes", "cn,sn,mail"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRepo).save(captor.capture());

        AuditEvent saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(AuditSource.INTERNAL);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getActorId()).isEqualTo(adminId);
        assertThat(saved.getActorType()).isEqualTo("ADMIN");
        assertThat(saved.getActorUsername()).isEqualTo("alice");
        assertThat(saved.getDirectoryId()).isEqualTo(directoryId);
        assertThat(saved.getDirectoryName()).isEqualTo("corp-ldap");
        assertThat(saved.getAction()).isEqualTo(AuditAction.USER_CREATE);
        assertThat(saved.getTargetDn()).isEqualTo("uid=alice,ou=users,dc=corp,dc=com");
        assertThat(saved.getDetail()).containsKey("attributes");
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void record_doesNotThrow_whenDirLookupFails() {
        AuthPrincipal principal = new AuthPrincipal(
                PrincipalType.ADMIN, adminId, tenantId, "bob");
        when(dirRepo.findById(directoryId)).thenReturn(Optional.empty());

        // Should silently swallow the missing-directory case
        auditService.record(principal, directoryId, AuditAction.USER_DELETE,
                "uid=bob,dc=corp", null);

        verify(auditRepo).save(any(AuditEvent.class));
    }

    @Test
    void record_doesNotPropagateException_whenSaveFails() {
        AuthPrincipal principal = new AuthPrincipal(
                PrincipalType.ADMIN, adminId, tenantId, "carol");
        DirectoryConnection dc = mockDirectory("dir");
        when(dirRepo.findById(directoryId)).thenReturn(Optional.of(dc));
        doThrow(new RuntimeException("DB down")).when(auditRepo).save(any());

        // Must not throw — audit failures are swallowed
        auditService.record(principal, directoryId, AuditAction.USER_ENABLE,
                "uid=carol,dc=corp", null);
    }

    // ── Changelog event recording ─────────────────────────────────────────────

    @Test
    void recordChangelogEvent_savesEvent_whenNotAlreadyRecorded() {
        String changeNumber = "12345";
        when(auditRepo.existsByDirectoryIdAndChangelogChangeNumber(directoryId, changeNumber))
                .thenReturn(false);

        auditService.recordChangelogEvent(
                tenantId, directoryId, "corp-ldap",
                "uid=dave,dc=corp", changeNumber,
                Map.of("changeType", "modify"),
                OffsetDateTime.now());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRepo).save(captor.capture());

        AuditEvent saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(AuditSource.LDAP_CHANGELOG);
        assertThat(saved.getAction()).isEqualTo(AuditAction.LDAP_CHANGE);
        assertThat(saved.getChangelogChangeNumber()).isEqualTo(changeNumber);
    }

    @Test
    void recordChangelogEvent_skips_whenAlreadyRecorded() {
        String changeNumber = "99999";
        when(auditRepo.existsByDirectoryIdAndChangelogChangeNumber(directoryId, changeNumber))
                .thenReturn(true);

        auditService.recordChangelogEvent(
                tenantId, directoryId, "corp-ldap",
                "uid=eve,dc=corp", changeNumber,
                Map.of("changeType", "add"),
                OffsetDateTime.now());

        verify(auditRepo, never()).save(any());
    }

    // ── isChangelogEventRecorded ──────────────────────────────────────────────

    @Test
    void isChangelogEventRecorded_delegatesToRepository() {
        when(auditRepo.existsByDirectoryIdAndChangelogChangeNumber(directoryId, "42"))
                .thenReturn(true);

        assertThat(auditService.isChangelogEventRecorded(directoryId, "42")).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DirectoryConnection mockDirectory(String name) {
        DirectoryConnection dc = mock(DirectoryConnection.class);
        when(dc.getDisplayName()).thenReturn(name);
        return dc;
    }
}
