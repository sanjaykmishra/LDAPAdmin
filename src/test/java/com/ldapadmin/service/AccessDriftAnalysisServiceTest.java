package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.drift.*;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.exception.LdapAdminException;
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
    @Mock private AccessSnapshotUserRepository userRepo;
    @Mock private AccessDriftFindingRepository findingRepo;
    @Mock private DirectoryConnectionRepository directoryRepo;
    @Mock private AccountRepository accountRepo;
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
                ruleRepo, snapshotRepo, membershipRepo, userRepo, findingRepo,
                directoryRepo, accountRepo, auditService);

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Directory");
    }

    @Test
    void analyze_detectsAnomalousAccess() {
        stubSnapshot();
        PeerGroupRule rule = buildRule("department", 25);
        when(ruleRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(rule));

        // 5 users in Engineering via snapshot user attrs
        when(userRepo.findBySnapshotId(snapshotId)).thenReturn(List.of(
                makeSnapshotUser("uid=alice,dc=test", "Engineering"),
                makeSnapshotUser("uid=bob,dc=test", "Engineering"),
                makeSnapshotUser("uid=carol,dc=test", "Engineering"),
                makeSnapshotUser("uid=dave,dc=test", "Engineering"),
                makeSnapshotUser("uid=eve,dc=test", "Engineering")));

        // All 5 in cn=devs, but only alice in cn=finance-ro (20% < 25% threshold)
        List<AccessSnapshotMembership> memberships = new ArrayList<>();
        for (String u : List.of("uid=alice,dc=test", "uid=bob,dc=test", "uid=carol,dc=test", "uid=dave,dc=test", "uid=eve,dc=test")) {
            memberships.add(makeMembership(u, "cn=devs,dc=test", "devs"));
        }
        memberships.add(makeMembership("uid=alice,dc=test", "cn=finance-ro,dc=test", "finance-ro"));
        when(membershipRepo.findBySnapshotId(snapshotId)).thenReturn(memberships);

        when(findingRepo.findExisting(any(), any(), any(), any())).thenReturn(List.of());
        when(findingRepo.findByDirectoryIdAndStatus(any(), eq(DriftFindingStatus.OPEN))).thenReturn(List.of());
        when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DriftAnalysisResult result = service.analyze(directoryId, snapshotId, principal);

        assertThat(result.totalFindings()).isEqualTo(1);
        assertThat(result.rulesEvaluated()).isEqualTo(1);
        assertThat(result.peerGroupsAnalyzed()).isEqualTo(1);
        verify(findingRepo, times(1)).save(any(AccessDriftFinding.class));
    }

    @Test
    void analyze_noRules_returnsEmpty() {
        stubSnapshot();
        when(ruleRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of());

        DriftAnalysisResult result = service.analyze(directoryId, snapshotId, principal);

        assertThat(result.rulesEvaluated()).isEqualTo(0);
        assertThat(result.totalFindings()).isEqualTo(0);
    }

    @Test
    void analyze_skipsExistingOpenFindings() {
        stubSnapshot();
        PeerGroupRule rule = buildRule("department", 25);
        when(ruleRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(rule));

        when(userRepo.findBySnapshotId(snapshotId)).thenReturn(List.of(
                makeSnapshotUser("uid=alice,dc=test", "Engineering"),
                makeSnapshotUser("uid=bob,dc=test", "Engineering"),
                makeSnapshotUser("uid=carol,dc=test", "Engineering"),
                makeSnapshotUser("uid=dave,dc=test", "Engineering"),
                makeSnapshotUser("uid=eve,dc=test", "Engineering")));

        List<AccessSnapshotMembership> memberships = new ArrayList<>();
        for (String u : List.of("uid=alice,dc=test", "uid=bob,dc=test", "uid=carol,dc=test", "uid=dave,dc=test", "uid=eve,dc=test")) {
            memberships.add(makeMembership(u, "cn=devs,dc=test", "devs"));
        }
        memberships.add(makeMembership("uid=alice,dc=test", "cn=secret,dc=test", "secret"));
        when(membershipRepo.findBySnapshotId(snapshotId)).thenReturn(memberships);

        when(findingRepo.findExisting(any(), eq("uid=alice,dc=test"), eq("cn=secret,dc=test"), eq(DriftFindingStatus.OPEN)))
                .thenReturn(List.of(new AccessDriftFinding()));
        when(findingRepo.findByDirectoryIdAndStatus(any(), eq(DriftFindingStatus.OPEN))).thenReturn(List.of());

        DriftAnalysisResult result = service.analyze(directoryId, snapshotId, principal);

        assertThat(result.existingSkipped()).isGreaterThan(0);
        verify(findingRepo, never()).save(any());
    }

    @Test
    void analyze_skipsTinyPeerGroups() {
        stubSnapshot();
        PeerGroupRule rule = buildRule("department", 10);
        when(ruleRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(rule));

        // Only 2 users — below 3-user minimum
        when(userRepo.findBySnapshotId(snapshotId)).thenReturn(List.of(
                makeSnapshotUser("uid=alice,dc=test", "Tiny"),
                makeSnapshotUser("uid=bob,dc=test", "Tiny")));
        when(membershipRepo.findBySnapshotId(snapshotId)).thenReturn(List.of());
        when(findingRepo.findByDirectoryIdAndStatus(any(), eq(DriftFindingStatus.OPEN))).thenReturn(List.of());

        DriftAnalysisResult result = service.analyze(directoryId, snapshotId, principal);

        assertThat(result.peerGroupsAnalyzed()).isEqualTo(0);
    }

    @Test
    void analyze_snapshotWrongDirectory_throws() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        DirectoryConnection otherDir = new DirectoryConnection();
        otherDir.setId(UUID.randomUUID());
        AccessSnapshot snapshot = new AccessSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setDirectory(otherDir);
        when(snapshotRepo.findById(snapshotId)).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> service.analyze(directoryId, snapshotId, principal))
                .isInstanceOf(com.ldapadmin.exception.ResourceNotFoundException.class);
    }

    @Test
    void acknowledgeFinding_updatesStatus() {
        AccessDriftFinding finding = new AccessDriftFinding();
        finding.setId(UUID.randomUUID());
        finding.setStatus(DriftFindingStatus.OPEN);
        finding.setRule(buildRule("department", 10));
        AccessSnapshot snap = buildSnapshot();
        finding.setSnapshot(snap);
        finding.setUserDn("uid=alice,dc=test");
        finding.setGroupDn("cn=test,dc=test");
        when(findingRepo.findById(finding.getId())).thenReturn(Optional.of(finding));
        Account actor = new Account();
        actor.setId(adminId);
        actor.setUsername("admin");
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(actor));
        when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DriftFindingResponse result = service.acknowledgeFinding(finding.getId(), principal);

        assertThat(result.status()).isEqualTo(DriftFindingStatus.ACKNOWLEDGED);
        verify(auditService).record(any(), eq(directoryId), any(), any(), any());
    }

    @Test
    void exemptFinding_updatesStatusWithReason() {
        AccessDriftFinding finding = new AccessDriftFinding();
        finding.setId(UUID.randomUUID());
        finding.setStatus(DriftFindingStatus.OPEN);
        finding.setRule(buildRule("department", 10));
        AccessSnapshot snap = buildSnapshot();
        finding.setSnapshot(snap);
        finding.setUserDn("uid=alice,dc=test");
        finding.setGroupDn("cn=test,dc=test");
        when(findingRepo.findById(finding.getId())).thenReturn(Optional.of(finding));
        Account actor = new Account();
        actor.setId(adminId);
        actor.setUsername("admin");
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(actor));
        when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DriftFindingResponse result = service.exemptFinding(finding.getId(), "Business need", principal);

        assertThat(result.status()).isEqualTo(DriftFindingStatus.EXEMPTED);
        assertThat(result.exemptionReason()).isEqualTo("Business need");
        verify(auditService).record(any(), any(), any(), any(), any());
    }

    @Test
    void createRule_validatesThresholds() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        var req = new PeerGroupRuleRequest("Bad Rule", "department", 20, 50, true);

        assertThatThrownBy(() -> service.createRule(directoryId, req, principal))
                .isInstanceOf(LdapAdminException.class)
                .hasMessageContaining("must be less than");
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
        verify(ruleRepo).save(any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void stubSnapshot() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        AccessSnapshot snapshot = new AccessSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setDirectory(directory);
        when(snapshotRepo.findById(snapshotId)).thenReturn(Optional.of(snapshot));
    }

    private AccessSnapshot buildSnapshot() {
        AccessSnapshot s = new AccessSnapshot();
        s.setId(snapshotId);
        s.setDirectory(directory);
        return s;
    }

    private PeerGroupRule buildRule(String attr, int anomalyPct) {
        PeerGroupRule r = new PeerGroupRule();
        r.setId(UUID.randomUUID());
        r.setName("Test Rule");
        r.setDirectory(directory);
        r.setGroupingAttribute(attr);
        r.setAnomalyThresholdPct(anomalyPct);
        r.setNormalThresholdPct(50);
        r.setEnabled(true);
        return r;
    }

    private AccessSnapshotUser makeSnapshotUser(String dn, String department) {
        AccessSnapshotUser su = new AccessSnapshotUser();
        su.setUserDn(dn);
        su.setDepartment(department);
        su.setDisplayName(dn);
        return su;
    }

    private AccessSnapshotMembership makeMembership(String userDn, String groupDn, String groupName) {
        AccessSnapshotMembership m = new AccessSnapshotMembership();
        m.setUserDn(userDn);
        m.setGroupDn(groupDn);
        m.setGroupName(groupName);
        return m;
    }
}
