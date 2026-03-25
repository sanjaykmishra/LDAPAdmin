package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.sod.*;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.exception.SodViolationException;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapUserService;
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
class SodPolicyServiceTest {

    @Mock private SodPolicyRepository policyRepo;
    @Mock private SodViolationRepository violationRepo;
    @Mock private DirectoryConnectionRepository directoryRepo;
    @Mock private AccountRepository accountRepo;
    @Mock private LdapGroupService ldapGroupService;
    @Mock private LdapUserService ldapUserService;
    @Mock private AuditService auditService;

    private SodPolicyService service;

    private final UUID directoryId = UUID.randomUUID();
    private final UUID otherDirectoryId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final AuthPrincipal principal = new AuthPrincipal(PrincipalType.SUPERADMIN, adminId, "admin");

    private DirectoryConnection directory;
    private DirectoryConnection otherDirectory;
    private Account adminAccount;

    @BeforeEach
    void setUp() {
        service = new SodPolicyService(
                policyRepo, violationRepo, directoryRepo, accountRepo,
                ldapGroupService, ldapUserService, auditService);

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Directory");

        otherDirectory = new DirectoryConnection();
        otherDirectory.setId(otherDirectoryId);
        otherDirectory.setDisplayName("Other Directory");

        adminAccount = new Account();
        adminAccount.setId(adminId);
        adminAccount.setUsername("admin");
    }

    // ── CRUD Tests ──────────────────────────────────────────────────────────

    @Test
    void create_validRequest_createsPolicyAndAudits() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(policyRepo.existsDuplicateGroupPair(any(), any(), any())).thenReturn(false);
        when(policyRepo.save(any())).thenAnswer(inv -> {
            SodPolicy p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        var req = new CreateSodPolicyRequest(
                "Finance vs Audit", "No dual access", "cn=finance,dc=example", "cn=audit,dc=example",
                "Finance", "Audit", SodSeverity.HIGH, SodAction.BLOCK, true);

        SodPolicy result = service.create(directoryId, req, principal);

        assertThat(result.getName()).isEqualTo("Finance vs Audit");
        assertThat(result.getSeverity()).isEqualTo(SodSeverity.HIGH);
        assertThat(result.getAction()).isEqualTo(SodAction.BLOCK);
        verify(auditService).record(eq(principal), eq(directoryId), eq(AuditAction.SOD_POLICY_CREATED), isNull(), any());
    }

    @Test
    void create_directoryNotFound_throws() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.empty());

        var req = new CreateSodPolicyRequest("X", null, "a", "b", null, null, SodSeverity.LOW, SodAction.ALERT, true);

        assertThatThrownBy(() -> service.create(directoryId, req, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_sameGroupDn_throws() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));

        var req = new CreateSodPolicyRequest("X", null,
                "cn=group,dc=example", "cn=group,dc=example",
                null, null, SodSeverity.LOW, SodAction.ALERT, true);

        assertThatThrownBy(() -> service.create(directoryId, req, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be the same");
    }

    @Test
    void create_duplicateGroupPair_throws() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(policyRepo.existsDuplicateGroupPair(directoryId, "cn=a,dc=example", "cn=b,dc=example"))
                .thenReturn(true);

        var req = new CreateSodPolicyRequest("X", null,
                "cn=a,dc=example", "cn=b,dc=example",
                null, null, SodSeverity.LOW, SodAction.ALERT, true);

        assertThatThrownBy(() -> service.create(directoryId, req, principal))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void update_updatesFieldsAndAudits() {
        SodPolicy existing = buildPolicy(SodAction.ALERT, SodSeverity.LOW);
        when(policyRepo.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(policyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpdateSodPolicyRequest("Updated Name", null, null, null, null, null, SodSeverity.HIGH, null, null);

        SodPolicy result = service.update(directoryId, existing.getId(), req, principal);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getSeverity()).isEqualTo(SodSeverity.HIGH);
        assertThat(result.getAction()).isEqualTo(SodAction.ALERT); // unchanged
        verify(auditService).record(eq(principal), eq(directoryId), eq(AuditAction.SOD_POLICY_UPDATED), isNull(), any());
    }

    @Test
    void delete_deletesAndAudits() {
        SodPolicy existing = buildPolicy(SodAction.ALERT, SodSeverity.LOW);
        when(policyRepo.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(violationRepo.countByPolicyIdAndStatus(existing.getId(), SodViolationStatus.OPEN)).thenReturn(2L);
        when(violationRepo.countByPolicyIdAndStatus(existing.getId(), SodViolationStatus.EXEMPTED)).thenReturn(1L);

        service.delete(directoryId, existing.getId(), principal);

        verify(policyRepo).delete(existing);
        verify(auditService).record(eq(principal), eq(directoryId), eq(AuditAction.SOD_POLICY_DELETED), isNull(), any());
    }

    @Test
    void listPolicies_returnsMappedResponses() {
        SodPolicy p = buildPolicy(SodAction.BLOCK, SodSeverity.HIGH);
        when(policyRepo.findByDirectoryId(directoryId)).thenReturn(List.of(p));
        when(violationRepo.countByPolicyIdAndStatus(any(), eq(SodViolationStatus.OPEN))).thenReturn(3L);

        List<SodPolicyResponse> result = service.listPolicies(directoryId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).openViolationCount()).isEqualTo(3);
        assertThat(result.get(0).action()).isEqualTo(SodAction.BLOCK);
    }

    // ── Directory ownership tests ───────────────────────────────────────────

    @Test
    void getPolicy_wrongDirectory_throws() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.LOW);
        when(policyRepo.findById(policy.getId())).thenReturn(Optional.of(policy));

        // policy belongs to directoryId, but we pass otherDirectoryId
        assertThatThrownBy(() -> service.getPolicy(otherDirectoryId, policy.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_wrongDirectory_throws() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.LOW);
        when(policyRepo.findById(policy.getId())).thenReturn(Optional.of(policy));

        var req = new UpdateSodPolicyRequest("X", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(otherDirectoryId, policy.getId(), req, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_wrongDirectory_throws() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.LOW);
        when(policyRepo.findById(policy.getId())).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.delete(otherDirectoryId, policy.getId(), principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void exemptViolation_wrongDirectory_throws() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.HIGH);
        SodViolation v = new SodViolation();
        v.setId(UUID.randomUUID());
        v.setPolicy(policy);
        v.setUserDn("uid=alice,dc=example");
        v.setStatus(SodViolationStatus.OPEN);

        when(violationRepo.findById(v.getId())).thenReturn(Optional.of(v));

        var req = new ExemptViolationRequest("reason", null);

        // violation's policy belongs to directoryId, but we pass otherDirectoryId
        assertThatThrownBy(() -> service.exemptViolation(otherDirectoryId, v.getId(), req, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveViolation_wrongDirectory_throws() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.LOW);
        SodViolation v = new SodViolation();
        v.setId(UUID.randomUUID());
        v.setPolicy(policy);
        v.setUserDn("uid=alice,dc=example");
        v.setStatus(SodViolationStatus.OPEN);

        when(violationRepo.findById(v.getId())).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.resolveViolation(otherDirectoryId, v.getId(), principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Scan Tests ──────────────────────────────────────────────────────────

    @Test
    void scanDirectory_detectsNewViolations() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.HIGH);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        // User "uid=alice" is in both groups
        when(ldapGroupService.getMembers(directory, policy.getGroupADn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example", "uid=bob,dc=example"));
        when(ldapGroupService.getMembers(directory, policy.getGroupBDn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example", "uid=charlie,dc=example"));

        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), any(), eq(SodViolationStatus.OPEN)))
                .thenReturn(List.of());
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), any(), eq(SodViolationStatus.EXEMPTED)))
                .thenReturn(List.of());
        when(violationRepo.findByPolicyId(policy.getId())).thenReturn(List.of());

        SodScanResultDto result = service.scanDirectory(directoryId, principal);

        assertThat(result.policiesScanned()).isEqualTo(1);
        assertThat(result.violationsFound()).isEqualTo(1);
        assertThat(result.newViolations()).isEqualTo(1);
        assertThat(result.resolvedViolations()).isEqualTo(0);

        verify(violationRepo).save(any(SodViolation.class));
        verify(auditService).record(eq(principal), eq(directoryId), eq(AuditAction.SOD_VIOLATION_DETECTED), eq("uid=alice,dc=example"), any());
    }

    @Test
    void scanDirectory_resolvesRemovedViolations() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.MEDIUM);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        // No intersection
        when(ldapGroupService.getMembers(directory, policy.getGroupADn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));
        when(ldapGroupService.getMembers(directory, policy.getGroupBDn(), "member"))
                .thenReturn(List.of("uid=bob,dc=example"));

        // Existing open violation for alice (no longer in both groups)
        SodViolation openViolation = new SodViolation();
        openViolation.setId(UUID.randomUUID());
        openViolation.setPolicy(policy);
        openViolation.setUserDn("uid=alice,dc=example");
        openViolation.setStatus(SodViolationStatus.OPEN);
        when(violationRepo.findByPolicyId(policy.getId())).thenReturn(List.of(openViolation));

        SodScanResultDto result = service.scanDirectory(directoryId, principal);

        assertThat(result.resolvedViolations()).isEqualTo(1);
        assertThat(openViolation.getStatus()).isEqualTo(SodViolationStatus.RESOLVED);
    }

    @Test
    void scanDirectory_skipsExistingOpenViolations() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.HIGH);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        when(ldapGroupService.getMembers(directory, policy.getGroupADn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));
        when(ldapGroupService.getMembers(directory, policy.getGroupBDn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));

        // Already has open violation
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), eq("uid=alice,dc=example"), eq(SodViolationStatus.OPEN)))
                .thenReturn(List.of(new SodViolation()));
        when(violationRepo.findByPolicyId(policy.getId())).thenReturn(List.of());

        SodScanResultDto result = service.scanDirectory(directoryId, principal);

        assertThat(result.newViolations()).isEqualTo(0);
    }

    @Test
    void scanDirectory_skipsExemptedUsers() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.HIGH);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        when(ldapGroupService.getMembers(directory, policy.getGroupADn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));
        when(ldapGroupService.getMembers(directory, policy.getGroupBDn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));

        // No open violation, but has active exemption (no expiry = permanent)
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), eq("uid=alice,dc=example"), eq(SodViolationStatus.OPEN)))
                .thenReturn(List.of());
        SodViolation exempted = new SodViolation();
        exempted.setStatus(SodViolationStatus.EXEMPTED);
        exempted.setExemptionExpiresAt(null); // permanent
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), eq("uid=alice,dc=example"), eq(SodViolationStatus.EXEMPTED)))
                .thenReturn(List.of(exempted));
        when(violationRepo.findByPolicyId(policy.getId())).thenReturn(List.of());

        SodScanResultDto result = service.scanDirectory(directoryId, principal);

        assertThat(result.violationsFound()).isEqualTo(1);
        assertThat(result.newViolations()).isEqualTo(0);
        verify(violationRepo, never()).save(any());
    }

    @Test
    void scanDirectory_createsViolationForExpiredExemption() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.HIGH);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        when(ldapGroupService.getMembers(directory, policy.getGroupADn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));
        when(ldapGroupService.getMembers(directory, policy.getGroupBDn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));

        // No open violation, but has an EXPIRED exemption
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), eq("uid=alice,dc=example"), eq(SodViolationStatus.OPEN)))
                .thenReturn(List.of());
        SodViolation expiredExemption = new SodViolation();
        expiredExemption.setStatus(SodViolationStatus.EXEMPTED);
        expiredExemption.setExemptionExpiresAt(OffsetDateTime.now().minusDays(1)); // expired
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), eq("uid=alice,dc=example"), eq(SodViolationStatus.EXEMPTED)))
                .thenReturn(List.of(expiredExemption));
        when(violationRepo.findByPolicyId(policy.getId())).thenReturn(List.of());

        SodScanResultDto result = service.scanDirectory(directoryId, principal);

        assertThat(result.newViolations()).isEqualTo(1);
        verify(violationRepo).save(any(SodViolation.class));
    }

    // ── checkMembership Tests ───────────────────────────────────────────────

    @Test
    void checkMembership_blockPolicy_throwsSodViolationException() {
        SodPolicy policy = buildPolicy(SodAction.BLOCK, SodSeverity.HIGH);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        // Adding user to group A, user already in group B
        when(ldapGroupService.getMembers(directory, policy.getGroupBDn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));
        // No exemption
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), eq("uid=alice,dc=example"), eq(SodViolationStatus.EXEMPTED)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.checkMembership(directoryId, "uid=alice,dc=example", policy.getGroupADn(), principal))
                .isInstanceOf(SodViolationException.class)
                .hasMessageContaining("SoD policy");

        verify(auditService).record(eq(principal), eq(directoryId), eq(AuditAction.SOD_VIOLATION_BLOCKED), eq("uid=alice,dc=example"), any());
    }

    @Test
    void checkMembership_blockPolicy_allowsWithActiveExemption() {
        SodPolicy policy = buildPolicy(SodAction.BLOCK, SodSeverity.HIGH);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        when(ldapGroupService.getMembers(directory, policy.getGroupBDn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));

        // Active exemption exists
        SodViolation exempted = new SodViolation();
        exempted.setStatus(SodViolationStatus.EXEMPTED);
        exempted.setExemptionExpiresAt(OffsetDateTime.now().plusDays(30)); // still valid
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), eq("uid=alice,dc=example"), eq(SodViolationStatus.EXEMPTED)))
                .thenReturn(List.of(exempted));

        // Should not throw — exemption overrides BLOCK
        service.checkMembership(directoryId, "uid=alice,dc=example", policy.getGroupADn(), principal);

        verify(violationRepo, never()).save(any());
    }

    @Test
    void checkMembership_blockPolicy_blocksWithExpiredExemption() {
        SodPolicy policy = buildPolicy(SodAction.BLOCK, SodSeverity.HIGH);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        when(ldapGroupService.getMembers(directory, policy.getGroupBDn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));

        // Expired exemption
        SodViolation expiredExemption = new SodViolation();
        expiredExemption.setStatus(SodViolationStatus.EXEMPTED);
        expiredExemption.setExemptionExpiresAt(OffsetDateTime.now().minusDays(1));
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), eq("uid=alice,dc=example"), eq(SodViolationStatus.EXEMPTED)))
                .thenReturn(List.of(expiredExemption));

        assertThatThrownBy(() -> service.checkMembership(directoryId, "uid=alice,dc=example", policy.getGroupADn(), principal))
                .isInstanceOf(SodViolationException.class);
    }

    @Test
    void checkMembership_alertPolicy_createsViolationButDoesNotThrow() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.MEDIUM);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        when(ldapGroupService.getMembers(directory, policy.getGroupBDn(), "member"))
                .thenReturn(List.of("uid=alice,dc=example"));
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), eq("uid=alice,dc=example"), eq(SodViolationStatus.EXEMPTED)))
                .thenReturn(List.of());
        when(violationRepo.findByPolicyIdAndUserDnIgnoreCaseAndStatus(any(), eq("uid=alice,dc=example"), eq(SodViolationStatus.OPEN)))
                .thenReturn(List.of());

        // Should not throw
        service.checkMembership(directoryId, "uid=alice,dc=example", policy.getGroupADn(), principal);

        verify(violationRepo).save(any(SodViolation.class));
        verify(auditService).record(eq(principal), eq(directoryId), eq(AuditAction.SOD_VIOLATION_DETECTED), eq("uid=alice,dc=example"), any());
    }

    @Test
    void checkMembership_noConflict_doesNothing() {
        SodPolicy policy = buildPolicy(SodAction.BLOCK, SodSeverity.HIGH);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        // User is NOT in the other group
        when(ldapGroupService.getMembers(directory, policy.getGroupBDn(), "member"))
                .thenReturn(List.of("uid=bob,dc=example"));

        // Should not throw
        service.checkMembership(directoryId, "uid=alice,dc=example", policy.getGroupADn(), principal);

        verify(violationRepo, never()).save(any());
    }

    @Test
    void checkMembership_unrelatedGroup_ignoresPolicy() {
        SodPolicy policy = buildPolicy(SodAction.BLOCK, SodSeverity.HIGH);
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(policyRepo.findByDirectoryIdAndEnabledTrue(directoryId)).thenReturn(List.of(policy));

        // Adding to a group not covered by any policy
        service.checkMembership(directoryId, "uid=alice,dc=example", "cn=unrelated,dc=example", principal);

        verify(ldapGroupService, never()).getMembers(any(), any(), any());
    }

    @Test
    void checkMembership_directoryNotFound_silentlyReturns() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.empty());

        // Should not throw
        service.checkMembership(directoryId, "uid=alice,dc=example", "cn=group,dc=example", principal);
    }

    // ── Violation Management Tests ──────────────────────────────────────────

    @Test
    void exemptViolation_updatesStatusAndAudits() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.HIGH);
        SodViolation v = new SodViolation();
        v.setId(UUID.randomUUID());
        v.setPolicy(policy);
        v.setUserDn("uid=alice,dc=example");
        v.setUserDisplayName("Alice");
        v.setStatus(SodViolationStatus.OPEN);
        v.setDetectedAt(OffsetDateTime.now().minusDays(1));

        when(violationRepo.findById(v.getId())).thenReturn(Optional.of(v));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(violationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(90);
        var req = new ExemptViolationRequest("Business need", expiresAt);
        SodViolationResponse result = service.exemptViolation(directoryId, v.getId(), req, principal);

        assertThat(result.status()).isEqualTo(SodViolationStatus.EXEMPTED);
        assertThat(result.exemptionReason()).isEqualTo("Business need");
        assertThat(result.exemptionExpiresAt()).isEqualTo(expiresAt);
        assertThat(v.getResolvedAt()).isNotNull();
        verify(auditService).record(eq(principal), eq(directoryId), eq(AuditAction.SOD_VIOLATION_EXEMPTED), eq("uid=alice,dc=example"), any());
    }

    @Test
    void exemptViolation_permanentExemption() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.HIGH);
        SodViolation v = new SodViolation();
        v.setId(UUID.randomUUID());
        v.setPolicy(policy);
        v.setUserDn("uid=alice,dc=example");
        v.setStatus(SodViolationStatus.OPEN);
        v.setDetectedAt(OffsetDateTime.now().minusDays(1));

        when(violationRepo.findById(v.getId())).thenReturn(Optional.of(v));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(violationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new ExemptViolationRequest("Permanent need", null);
        SodViolationResponse result = service.exemptViolation(directoryId, v.getId(), req, principal);

        assertThat(result.exemptionExpiresAt()).isNull();
    }

    @Test
    void resolveViolation_updatesStatusAndAudits() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.LOW);
        SodViolation v = new SodViolation();
        v.setId(UUID.randomUUID());
        v.setPolicy(policy);
        v.setUserDn("uid=alice,dc=example");
        v.setStatus(SodViolationStatus.OPEN);

        when(violationRepo.findById(v.getId())).thenReturn(Optional.of(v));
        when(violationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SodViolationResponse result = service.resolveViolation(directoryId, v.getId(), principal);

        assertThat(result.status()).isEqualTo(SodViolationStatus.RESOLVED);
        assertThat(v.getResolvedAt()).isNotNull();
        verify(auditService).record(eq(principal), eq(directoryId), eq(AuditAction.SOD_VIOLATION_RESOLVED), eq("uid=alice,dc=example"), any());
    }

    @Test
    void listViolations_withStatusFilter_returnsFiltered() {
        SodViolation v = new SodViolation();
        v.setId(UUID.randomUUID());
        SodPolicy p = buildPolicy(SodAction.ALERT, SodSeverity.LOW);
        v.setPolicy(p);
        v.setUserDn("uid=alice,dc=example");
        v.setStatus(SodViolationStatus.OPEN);
        v.setDetectedAt(OffsetDateTime.now());

        when(violationRepo.findByDirectoryIdAndStatus(directoryId, SodViolationStatus.OPEN))
                .thenReturn(List.of(v));

        List<SodViolationResponse> result = service.listViolations(directoryId, SodViolationStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(SodViolationStatus.OPEN);
        // Verify group info is included in response
        assertThat(result.get(0).groupADn()).isEqualTo(p.getGroupADn());
        assertThat(result.get(0).groupBDn()).isEqualTo(p.getGroupBDn());
    }

    @Test
    void listViolations_withoutFilter_returnsAll() {
        when(violationRepo.findByDirectoryId(directoryId)).thenReturn(List.of());

        List<SodViolationResponse> result = service.listViolations(directoryId, null);

        assertThat(result).isEmpty();
        verify(violationRepo).findByDirectoryId(directoryId);
    }

    @Test
    void reopenExpiredExemptions_reopensExpired() {
        SodPolicy policy = buildPolicy(SodAction.ALERT, SodSeverity.HIGH);
        SodViolation v = new SodViolation();
        v.setId(UUID.randomUUID());
        v.setPolicy(policy);
        v.setUserDn("uid=alice,dc=example");
        v.setStatus(SodViolationStatus.EXEMPTED);
        v.setExemptionExpiresAt(OffsetDateTime.now().minusDays(1));
        v.setExemptionReason("Temporary");
        v.setResolvedAt(OffsetDateTime.now().minusDays(30));

        when(violationRepo.findExpiredExemptions(eq(SodViolationStatus.EXEMPTED), any()))
                .thenReturn(List.of(v));
        when(violationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int count = service.reopenExpiredExemptions();

        assertThat(count).isEqualTo(1);
        assertThat(v.getStatus()).isEqualTo(SodViolationStatus.OPEN);
        assertThat(v.getResolvedAt()).isNull();
        assertThat(v.getExemptionReason()).isNull();
        assertThat(v.getExemptionExpiresAt()).isNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private SodPolicy buildPolicy(SodAction action, SodSeverity severity) {
        SodPolicy policy = new SodPolicy();
        policy.setId(UUID.randomUUID());
        policy.setName("Test Policy");
        policy.setDirectory(directory);
        policy.setGroupADn("cn=group-a,dc=example,dc=com");
        policy.setGroupBDn("cn=group-b,dc=example,dc=com");
        policy.setGroupAName("Group A");
        policy.setGroupBName("Group B");
        policy.setSeverity(severity);
        policy.setAction(action);
        policy.setEnabled(true);
        policy.setCreatedBy(adminAccount);
        policy.setCreatedAt(OffsetDateTime.now());
        return policy;
    }
}
