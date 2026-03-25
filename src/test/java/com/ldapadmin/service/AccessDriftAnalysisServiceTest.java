package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.drift.*;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessDriftAnalysisServiceTest {

    @Mock private PeerGroupRuleRepository ruleRepo;
    @Mock private AccessSnapshotRepository snapshotRepo;
    @Mock private AccessSnapshotMembershipRepository membershipRepo;
    @Mock private AccessDriftFindingRepository findingRepo;
    @Mock private DirectoryConnectionRepository directoryRepo;
    @Mock private AccountRepository accountRepo;
    @Mock private LdapUserService ldapUserService;
    @Mock private AuditService auditService;

    private AccessDriftAnalysisService service;

    private final UUID directoryId = UUID.randomUUID();
    private final UUID snapshotId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final AuthPrincipal principal = new AuthPrincipal(PrincipalType.SUPERADMIN, adminId, "admin");

    private DirectoryConnection directory;

    @BeforeEach
    void setUp() {
        service = new AccessDriftAnalysisService(
                ruleRepo, snapshotRepo, membershipRepo, findingRepo,
                directoryRepo, accountRepo, ldapUserService, auditService);

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Directory");
    }

    @Test
    void analyze_detectsAnomalousAccess() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        AccessSnapshot snapshot = new AccessSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setDirectory(directory);
        when(snapshotRepo.findById(snapshotId)).thenReturn(Optional.of(snapshot));

        // One rule: group by department, anomaly threshold 10%
        PeerGroupRule rule = new PeerGroupRule();
        rule.setId(UUID.randomUUID());
        rule.setDirectory(directory);
        rule.setGroupingAttribute("department");
        rule.setAnomalyThresholdPct(10);
        rule.setEnabled(true);
        when(ruleRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(rule));

        // 5 users in Engineering peer group
        when(ldapUserService.searchUsers(eq(directory), eq("(department=*)"), any(), anyInt(), any(), any()))
                .thenReturn(List.of(
                        new LdapUser("uid=alice,dc=test", Map.of("department", List.of("Engineering"))),
                        new LdapUser("uid=bob,dc=test", Map.of("department", List.of("Engineering"))),
                        new LdapUser("uid=carol,dc=test", Map.of("department", List.of("Engineering"))),
                        new LdapUser("uid=dave,dc=test", Map.of("department", List.of("Engineering"))),
                        new LdapUser("uid=eve,dc=test", Map.of("department", List.of("Engineering")))
                ));

        // Snapshot memberships: all 5 in cn=devs, but only alice in cn=finance-ro
        List<AccessSnapshotMembership> memberships = new ArrayList<>();
        for (String user : List.of("uid=alice,dc=test", "uid=bob,dc=test", "uid=carol,dc=test", "uid=dave,dc=test", "uid=eve,dc=test")) {
            AccessSnapshotMembership m = new AccessSnapshotMembership();
            m.setSnapshot(snapshot);
            m.setUserDn(user);
            m.setGroupDn("cn=devs,dc=test");
            m.setGroupName("devs");
            memberships.add(m);
        }
        // Alice also in finance-ro (anomalous — only 1/5 = 20%, but at 10% threshold only flagged if <10%)
        // Let's make the threshold higher to trigger: anomalyThresholdPct=25
        rule.setAnomalyThresholdPct(25);
        AccessSnapshotMembership aliceFinance = new AccessSnapshotMembership();
        aliceFinance.setSnapshot(snapshot);
        aliceFinance.setUserDn("uid=alice,dc=test");
        aliceFinance.setGroupDn("cn=finance-ro,dc=test");
        aliceFinance.setGroupName("finance-ro");
        memberships.add(aliceFinance);

        when(membershipRepo.findBySnapshotId(snapshotId)).thenReturn(memberships);
        when(findingRepo.findExisting(any(), any(), any(), any())).thenReturn(List.of());
        when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DriftAnalysisResult result = service.analyze(directoryId, snapshotId, principal);

        assertThat(result.totalFindings()).isEqualTo(1); // alice in finance-ro (20% < 25% threshold)
        assertThat(result.rulesEvaluated()).isEqualTo(1);
        assertThat(result.peerGroupsAnalyzed()).isEqualTo(1);
        verify(findingRepo, times(1)).save(any(AccessDriftFinding.class));
    }

    @Test
    void analyze_noRules_returnsEmpty() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        AccessSnapshot snapshot = new AccessSnapshot();
        snapshot.setId(snapshotId);
        when(snapshotRepo.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(ruleRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of());

        DriftAnalysisResult result = service.analyze(directoryId, snapshotId, principal);

        assertThat(result.rulesEvaluated()).isEqualTo(0);
        assertThat(result.totalFindings()).isEqualTo(0);
    }

    @Test
    void analyze_skipsExistingOpenFindings() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        AccessSnapshot snapshot = new AccessSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setDirectory(directory);
        when(snapshotRepo.findById(snapshotId)).thenReturn(Optional.of(snapshot));

        PeerGroupRule rule = new PeerGroupRule();
        rule.setId(UUID.randomUUID());
        rule.setDirectory(directory);
        rule.setGroupingAttribute("department");
        rule.setAnomalyThresholdPct(25);
        rule.setEnabled(true);
        when(ruleRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(rule));

        // Need 5 peers so 1/5 = 20% < 25% threshold triggers anomaly
        when(ldapUserService.searchUsers(eq(directory), any(), any(), anyInt(), any(), any()))
                .thenReturn(List.of(
                        new LdapUser("uid=alice,dc=test", Map.of("department", List.of("Engineering"))),
                        new LdapUser("uid=bob,dc=test", Map.of("department", List.of("Engineering"))),
                        new LdapUser("uid=carol,dc=test", Map.of("department", List.of("Engineering"))),
                        new LdapUser("uid=dave,dc=test", Map.of("department", List.of("Engineering"))),
                        new LdapUser("uid=eve,dc=test", Map.of("department", List.of("Engineering")))
                ));

        List<AccessSnapshotMembership> memberships = new ArrayList<>();
        for (String user : List.of("uid=alice,dc=test", "uid=bob,dc=test", "uid=carol,dc=test", "uid=dave,dc=test", "uid=eve,dc=test")) {
            AccessSnapshotMembership m = new AccessSnapshotMembership();
            m.setSnapshot(snapshot);
            m.setUserDn(user);
            m.setGroupDn("cn=devs,dc=test");
            m.setGroupName("devs");
            memberships.add(m);
        }
        AccessSnapshotMembership aliceAnomaly = new AccessSnapshotMembership();
        aliceAnomaly.setSnapshot(snapshot);
        aliceAnomaly.setUserDn("uid=alice,dc=test");
        aliceAnomaly.setGroupDn("cn=secret,dc=test");
        aliceAnomaly.setGroupName("secret");
        memberships.add(aliceAnomaly);

        when(membershipRepo.findBySnapshotId(snapshotId)).thenReturn(memberships);
        // Already has an open finding for this
        when(findingRepo.findExisting(any(), eq("uid=alice,dc=test"), eq("cn=secret,dc=test"), eq(DriftFindingStatus.OPEN)))
                .thenReturn(List.of(new AccessDriftFinding()));

        DriftAnalysisResult result = service.analyze(directoryId, snapshotId, principal);

        assertThat(result.existingSkipped()).isGreaterThan(0);
        verify(findingRepo, never()).save(any());
    }

    @Test
    void analyze_skipsTinyPeerGroups() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        AccessSnapshot snapshot = new AccessSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setDirectory(directory);
        when(snapshotRepo.findById(snapshotId)).thenReturn(Optional.of(snapshot));

        PeerGroupRule rule = new PeerGroupRule();
        rule.setId(UUID.randomUUID());
        rule.setDirectory(directory);
        rule.setGroupingAttribute("department");
        rule.setAnomalyThresholdPct(10);
        rule.setEnabled(true);
        when(ruleRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(rule));

        // Only 2 users in a department — below the 3-user minimum
        when(ldapUserService.searchUsers(eq(directory), any(), any(), anyInt(), any(), any()))
                .thenReturn(List.of(
                        new LdapUser("uid=alice,dc=test", Map.of("department", List.of("Tiny"))),
                        new LdapUser("uid=bob,dc=test", Map.of("department", List.of("Tiny")))
                ));

        when(membershipRepo.findBySnapshotId(snapshotId)).thenReturn(List.of());

        DriftAnalysisResult result = service.analyze(directoryId, snapshotId, principal);

        assertThat(result.peerGroupsAnalyzed()).isEqualTo(0);
        assertThat(result.totalFindings()).isEqualTo(0);
    }

    @Test
    void acknowledgeFinding_updatesStatus() {
        AccessDriftFinding finding = new AccessDriftFinding();
        finding.setId(UUID.randomUUID());
        finding.setStatus(DriftFindingStatus.OPEN);
        finding.setRule(buildRule());
        when(findingRepo.findById(finding.getId())).thenReturn(Optional.of(finding));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(new Account()));
        when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DriftFindingResponse result = service.acknowledgeFinding(finding.getId(), principal);

        assertThat(result.status()).isEqualTo(DriftFindingStatus.ACKNOWLEDGED);
    }

    @Test
    void exemptFinding_updatesStatusWithReason() {
        AccessDriftFinding finding = new AccessDriftFinding();
        finding.setId(UUID.randomUUID());
        finding.setStatus(DriftFindingStatus.OPEN);
        finding.setRule(buildRule());
        when(findingRepo.findById(finding.getId())).thenReturn(Optional.of(finding));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(new Account()));
        when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DriftFindingResponse result = service.exemptFinding(finding.getId(), "Business need", principal);

        assertThat(result.status()).isEqualTo(DriftFindingStatus.EXEMPTED);
        assertThat(result.exemptionReason()).isEqualTo("Business need");
    }

    @Test
    void createRule_savesAndReturns() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(new Account()));
        when(ruleRepo.save(any())).thenAnswer(inv -> {
            PeerGroupRule r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(OffsetDateTime.now());
            return r;
        });

        var req = new PeerGroupRuleRequest("By Department", "department", 50, 10, true);
        PeerGroupRuleResponse result = service.createRule(directoryId, req, principal);

        assertThat(result.name()).isEqualTo("By Department");
        assertThat(result.groupingAttribute()).isEqualTo("department");
        verify(ruleRepo).save(any());
    }

    private PeerGroupRule buildRule() {
        PeerGroupRule r = new PeerGroupRule();
        r.setId(UUID.randomUUID());
        r.setName("Test Rule");
        r.setDirectory(directory);
        return r;
    }
}
