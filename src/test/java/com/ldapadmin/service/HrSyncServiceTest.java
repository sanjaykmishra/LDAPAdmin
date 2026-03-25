package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.entity.hr.HrConnection;
import com.ldapadmin.entity.hr.HrEmployee;
import com.ldapadmin.entity.hr.HrSyncRun;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.hr.HrConnectionRepository;
import com.ldapadmin.repository.hr.HrEmployeeRepository;
import com.ldapadmin.repository.hr.HrSyncRunRepository;
import com.ldapadmin.service.hr.BambooHrClient;
import com.ldapadmin.service.hr.HrSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HrSyncServiceTest {

    @Mock private BambooHrClient bambooHrClient;
    @Mock private HrConnectionRepository connectionRepo;
    @Mock private HrEmployeeRepository employeeRepo;
    @Mock private HrSyncRunRepository syncRunRepo;
    @Mock private DirectoryConnectionRepository directoryRepo;
    @Mock private LdapUserService ldapUserService;
    @Mock private EncryptionService encryptionService;
    @Mock private AuditService auditService;

    private HrSyncService service;

    private final UUID directoryId = UUID.randomUUID();
    private DirectoryConnection directory;
    private HrConnection hrConnection;
    private AuthPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new HrSyncService(bambooHrClient, connectionRepo, employeeRepo,
                syncRunRepo, directoryRepo, ldapUserService, encryptionService, auditService);

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Dir");
        directory.setBaseDn("dc=test,dc=com");

        hrConnection = new HrConnection();
        hrConnection.setId(UUID.randomUUID());
        hrConnection.setDirectory(directory);
        hrConnection.setProvider(HrProvider.BAMBOOHR);
        hrConnection.setSubdomain("acme");
        hrConnection.setApiKeyEncrypted("encrypted-key");
        hrConnection.setMatchAttribute("mail");
        hrConnection.setMatchField("workEmail");

        principal = new AuthPrincipal(PrincipalType.SUPERADMIN, UUID.randomUUID(), "admin");
    }

    @Test
    void sync_successfulSync_returnsSuccessRun() {
        when(encryptionService.decrypt("encrypted-key")).thenReturn("api-key-123");
        when(bambooHrClient.fetchAllEmployees("acme", "api-key-123"))
                .thenReturn(List.of(
                        Map.of("id", "1", "firstName", "John", "lastName", "Doe",
                                "workEmail", "john@example.com", "status", "Active"),
                        Map.of("id", "2", "firstName", "Jane", "lastName", "Smith",
                                "workEmail", "jane@example.com", "status", "Active")
                ));
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(employeeRepo.findByHrConnectionIdAndEmployeeId(any(), any())).thenReturn(Optional.empty());
        when(syncRunRepo.save(any())).thenAnswer(inv -> {
            HrSyncRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // No LDAP match found
        when(ldapUserService.searchUsers(any(), anyString(), any(), anyInt(), any()))
                .thenReturn(List.of());

        HrSyncRun result = service.sync(hrConnection, HrSyncTrigger.MANUAL, principal);

        assertThat(result.getStatus()).isEqualTo(HrSyncStatus.SUCCESS);
        assertThat(result.getTotalEmployees()).isEqualTo(2);
        assertThat(result.getNewEmployees()).isEqualTo(2);
        assertThat(result.getUnmatchedCount()).isEqualTo(2);
        verify(employeeRepo, times(2)).save(any(HrEmployee.class));
    }

    @Test
    void sync_withLdapMatch_setsMatchedDn() {
        when(encryptionService.decrypt("encrypted-key")).thenReturn("api-key-123");
        when(bambooHrClient.fetchAllEmployees("acme", "api-key-123"))
                .thenReturn(List.of(
                        Map.of("id", "1", "workEmail", "john@example.com", "status", "Active")
                ));
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(employeeRepo.findByHrConnectionIdAndEmployeeId(any(), eq("1"))).thenReturn(Optional.empty());
        when(syncRunRepo.save(any())).thenAnswer(inv -> {
            HrSyncRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LdapUser ldapUser = new LdapUser("uid=john,dc=test,dc=com", Map.of());
        when(ldapUserService.searchUsers(eq(directory), contains("john@example.com"), any(), eq(2), any()))
                .thenReturn(List.of(ldapUser));

        HrSyncRun result = service.sync(hrConnection, HrSyncTrigger.MANUAL, principal);

        assertThat(result.getMatchedCount()).isEqualTo(1);
        assertThat(result.getUnmatchedCount()).isEqualTo(0);

        ArgumentCaptor<HrEmployee> captor = ArgumentCaptor.forClass(HrEmployee.class);
        verify(employeeRepo).save(captor.capture());
        assertThat(captor.getValue().getMatchedLdapDn()).isEqualTo("uid=john,dc=test,dc=com");
        assertThat(captor.getValue().getMatchConfidence()).isEqualTo(HrMatchConfidence.EXACT);
    }

    @Test
    void sync_terminatedWithLdapMatch_countsAsOrphaned() {
        when(encryptionService.decrypt("encrypted-key")).thenReturn("api-key-123");
        when(bambooHrClient.fetchAllEmployees("acme", "api-key-123"))
                .thenReturn(List.of(
                        Map.of("id", "1", "workEmail", "gone@example.com", "status", "Terminated")
                ));
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(employeeRepo.findByHrConnectionIdAndEmployeeId(any(), eq("1"))).thenReturn(Optional.empty());
        when(syncRunRepo.save(any())).thenAnswer(inv -> {
            HrSyncRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LdapUser ldapUser = new LdapUser("uid=gone,dc=test,dc=com", Map.of());
        when(ldapUserService.searchUsers(eq(directory), contains("gone@example.com"), any(), eq(2), any()))
                .thenReturn(List.of(ldapUser));

        HrSyncRun result = service.sync(hrConnection, HrSyncTrigger.MANUAL, principal);

        assertThat(result.getTerminatedCount()).isEqualTo(1);
        assertThat(result.getOrphanedCount()).isEqualTo(1);
    }

    @Test
    void sync_existingEmployee_updatesInsteadOfCreating() {
        when(encryptionService.decrypt("encrypted-key")).thenReturn("api-key-123");
        when(bambooHrClient.fetchAllEmployees("acme", "api-key-123"))
                .thenReturn(List.of(
                        Map.of("id", "1", "workEmail", "john@example.com", "status", "Active",
                                "department", "Engineering")
                ));
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        HrEmployee existing = new HrEmployee();
        existing.setId(UUID.randomUUID());
        existing.setHrConnection(hrConnection);
        existing.setEmployeeId("1");
        existing.setDepartment("Sales");
        when(employeeRepo.findByHrConnectionIdAndEmployeeId(any(), eq("1")))
                .thenReturn(Optional.of(existing));

        when(syncRunRepo.save(any())).thenAnswer(inv -> {
            HrSyncRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ldapUserService.searchUsers(any(), anyString(), any(), anyInt(), any()))
                .thenReturn(List.of());

        HrSyncRun result = service.sync(hrConnection, HrSyncTrigger.MANUAL, principal);

        assertThat(result.getNewEmployees()).isEqualTo(0);
        assertThat(result.getUpdatedEmployees()).isEqualTo(1);

        ArgumentCaptor<HrEmployee> captor = ArgumentCaptor.forClass(HrEmployee.class);
        verify(employeeRepo).save(captor.capture());
        assertThat(captor.getValue().getDepartment()).isEqualTo("Engineering");
    }

    @Test
    void sync_apiFails_returnsFailedRun() {
        when(encryptionService.decrypt("encrypted-key")).thenReturn("api-key-123");
        when(bambooHrClient.fetchAllEmployees("acme", "api-key-123"))
                .thenThrow(new RuntimeException("Connection refused"));
        when(syncRunRepo.save(any())).thenAnswer(inv -> {
            HrSyncRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HrSyncRun result = service.sync(hrConnection, HrSyncTrigger.MANUAL, principal);

        assertThat(result.getStatus()).isEqualTo(HrSyncStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("Connection refused");
    }

    @Test
    void sync_ambiguousLdapMatch_doesNotMatch() {
        when(encryptionService.decrypt("encrypted-key")).thenReturn("api-key-123");
        when(bambooHrClient.fetchAllEmployees("acme", "api-key-123"))
                .thenReturn(List.of(
                        Map.of("id", "1", "workEmail", "dupe@example.com", "status", "Active")
                ));
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(employeeRepo.findByHrConnectionIdAndEmployeeId(any(), eq("1"))).thenReturn(Optional.empty());
        when(syncRunRepo.save(any())).thenAnswer(inv -> {
            HrSyncRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Multiple LDAP matches = ambiguous
        when(ldapUserService.searchUsers(eq(directory), contains("dupe@example.com"), any(), eq(2), any()))
                .thenReturn(List.of(
                        new LdapUser("uid=user1,dc=test", Map.of()),
                        new LdapUser("uid=user2,dc=test", Map.of())
                ));

        HrSyncRun result = service.sync(hrConnection, HrSyncTrigger.MANUAL, principal);

        assertThat(result.getUnmatchedCount()).isEqualTo(1);
        assertThat(result.getMatchedCount()).isEqualTo(0);
    }

    @Test
    void sync_employeeWithNoEmail_skipsLdapMatch() {
        when(encryptionService.decrypt("encrypted-key")).thenReturn("api-key-123");
        when(bambooHrClient.fetchAllEmployees("acme", "api-key-123"))
                .thenReturn(List.of(
                        Map.of("id", "1", "firstName", "NoEmail", "status", "Active")
                ));
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(employeeRepo.findByHrConnectionIdAndEmployeeId(any(), eq("1"))).thenReturn(Optional.empty());
        when(syncRunRepo.save(any())).thenAnswer(inv -> {
            HrSyncRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HrSyncRun result = service.sync(hrConnection, HrSyncTrigger.MANUAL, principal);

        // No LDAP search should be attempted when email is null
        verify(ldapUserService, never()).searchUsers(any(), anyString(), any(), anyInt(), any());
        assertThat(result.getUnmatchedCount()).isEqualTo(1);
    }

    @Test
    void sync_nullPrincipal_skipsAuditRecording() {
        when(encryptionService.decrypt("encrypted-key")).thenReturn("api-key-123");
        when(bambooHrClient.fetchAllEmployees("acme", "api-key-123")).thenReturn(List.of());
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(syncRunRepo.save(any())).thenAnswer(inv -> {
            HrSyncRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sync(hrConnection, HrSyncTrigger.SCHEDULED, null);

        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void sync_recordsAuditEvents() {
        when(encryptionService.decrypt("encrypted-key")).thenReturn("api-key-123");
        when(bambooHrClient.fetchAllEmployees("acme", "api-key-123")).thenReturn(List.of());
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(syncRunRepo.save(any())).thenAnswer(inv -> {
            HrSyncRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sync(hrConnection, HrSyncTrigger.MANUAL, principal);

        verify(auditService).record(eq(principal), eq(directoryId),
                eq(AuditAction.HR_SYNC_STARTED), any(), any());
        verify(auditService).record(eq(principal), eq(directoryId),
                eq(AuditAction.HR_SYNC_COMPLETED), any(), any());
    }

    @Test
    void escapeLdapFilter_escapesSpecialCharacters() {
        assertThat(HrSyncService.escapeLdapFilter("normal")).isEqualTo("normal");
        assertThat(HrSyncService.escapeLdapFilter("john(doe)")).isEqualTo("john\\28doe\\29");
        assertThat(HrSyncService.escapeLdapFilter("a*b")).isEqualTo("a\\2ab");
        assertThat(HrSyncService.escapeLdapFilter("a\\b")).isEqualTo("a\\5cb");
    }

    @Test
    void sync_matchByEmployeeId_usesEmployeeIdField() {
        hrConnection.setMatchField("employeeId");
        hrConnection.setMatchAttribute("employeeNumber");

        when(encryptionService.decrypt("encrypted-key")).thenReturn("api-key-123");
        when(bambooHrClient.fetchAllEmployees("acme", "api-key-123"))
                .thenReturn(List.of(
                        Map.of("id", "42", "workEmail", "john@example.com", "status", "Active")
                ));
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(employeeRepo.findByHrConnectionIdAndEmployeeId(any(), eq("42"))).thenReturn(Optional.empty());
        when(syncRunRepo.save(any())).thenAnswer(inv -> {
            HrSyncRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(connectionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LdapUser ldapUser = new LdapUser("uid=john,dc=test", Map.of());
        when(ldapUserService.searchUsers(eq(directory), contains("employeeNumber=42"), any(), eq(2), any()))
                .thenReturn(List.of(ldapUser));

        HrSyncRun result = service.sync(hrConnection, HrSyncTrigger.MANUAL, principal);

        assertThat(result.getMatchedCount()).isEqualTo(1);
    }
}
