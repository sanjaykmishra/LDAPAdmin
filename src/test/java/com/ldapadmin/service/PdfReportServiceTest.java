package com.ldapadmin.service;

import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.model.LdapGroup;
import com.ldapadmin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfReportServiceTest {

    @Mock private ApplicationSettingsService settingsService;
    @Mock private LdapGroupService ldapGroupService;
    @Mock private DirectoryConnectionRepository directoryRepo;
    @Mock private AccessReviewCampaignRepository campaignRepo;
    @Mock private AccountRepository accountRepo;
    @Mock private AdminProfileRoleRepository profileRoleRepo;
    @Mock private AdminFeaturePermissionRepository featurePermRepo;

    private PdfReportService service;

    private final UUID directoryId = UUID.randomUUID();
    private DirectoryConnection directory;
    private ApplicationSettings settings;

    @BeforeEach
    void setUp() {
        service = new PdfReportService(
                settingsService, ldapGroupService, directoryRepo,
                campaignRepo, accountRepo, profileRoleRepo, featurePermRepo);

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Directory");
        directory.setBaseDn("dc=example,dc=com");
        directory.setPagingSize(500);

        settings = new ApplicationSettings();
        settings.setAppName("Test LDAP Portal");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void stubSettings() {
        when(settingsService.getEntity()).thenReturn(settings);
    }

    private LdapGroup makeGroup(String dn, String cn, List<String> members) {
        Map<String, List<String>> attrs = new LinkedHashMap<>();
        attrs.put("cn", List.of(cn));
        if (!members.isEmpty()) {
            attrs.put("member", members);
        }
        return new LdapGroup(dn, attrs);
    }

    private Account makeAccount(UUID id, String username, AccountRole role) {
        Account account = new Account();
        account.setId(id);
        account.setUsername(username);
        account.setDisplayName(username + " display");
        account.setRole(role);
        account.setAuthType(AccountType.LOCAL);
        account.setActive(true);
        account.setLastLoginAt(Instant.now());
        return account;
    }

    private void assertValidPdf(byte[] pdf) {
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
        // PDF files start with %PDF-
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    // ── User Access Report ─────────────────────────────────────────────────

    @Test
    void userAccessReport_withGroups_generatesValidPdf() throws IOException {
        stubSettings();
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        LdapGroup group1 = makeGroup("cn=admins,dc=example,dc=com", "admins",
                List.of("uid=alice,dc=example,dc=com", "uid=bob,dc=example,dc=com"));
        LdapGroup group2 = makeGroup("cn=devs,dc=example,dc=com", "devs",
                List.of("uid=carol,dc=example,dc=com"));

        when(ldapGroupService.searchGroups(eq(directory), anyString(), isNull(), eq(500),
                eq("cn"), eq("member"), eq("uniqueMember"), eq("memberUid")))
                .thenReturn(List.of(group1, group2));

        byte[] pdf = service.generateUserAccessReport(directoryId, null);
        assertValidPdf(pdf);
    }

    @Test
    void userAccessReport_withGroupDnFilter_queriesSingleGroup() throws IOException {
        stubSettings();
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        String filterDn = "cn=admins,dc=example,dc=com";
        LdapGroup group = makeGroup(filterDn, "admins",
                List.of("uid=alice,dc=example,dc=com"));

        when(ldapGroupService.getGroup(directory, filterDn, "cn", "member", "uniqueMember", "memberUid"))
                .thenReturn(group);

        byte[] pdf = service.generateUserAccessReport(directoryId, filterDn);
        assertValidPdf(pdf);
    }

    @Test
    void userAccessReport_emptyGroups_generatesValidPdf() throws IOException {
        stubSettings();
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        when(ldapGroupService.searchGroups(eq(directory), anyString(), isNull(), eq(500),
                eq("cn"), eq("member"), eq("uniqueMember"), eq("memberUid")))
                .thenReturn(List.of());

        byte[] pdf = service.generateUserAccessReport(directoryId, null);
        assertValidPdf(pdf);
    }

    @Test
    void userAccessReport_groupWithNoMembers_showsPlaceholder() throws IOException {
        stubSettings();
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        LdapGroup emptyGroup = makeGroup("cn=empty,dc=example,dc=com", "empty", List.of());
        when(ldapGroupService.searchGroups(eq(directory), anyString(), isNull(), eq(500),
                eq("cn"), eq("member"), eq("uniqueMember"), eq("memberUid")))
                .thenReturn(List.of(emptyGroup));

        byte[] pdf = service.generateUserAccessReport(directoryId, null);
        assertValidPdf(pdf);
    }

    @Test
    void userAccessReport_directoryNotFound_throws() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateUserAccessReport(directoryId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Directory not found");
    }

    // ── Access Review Summary ──────────────────────────────────────────────

    @Test
    void accessReviewSummary_withDecisions_generatesValidPdf() throws IOException {
        stubSettings();
        UUID campaignId = UUID.randomUUID();
        AccessReviewCampaign campaign = buildCampaign(campaignId);

        when(campaignRepo.findById(campaignId)).thenReturn(Optional.of(campaign));

        byte[] pdf = service.generateAccessReviewSummary(campaignId);
        assertValidPdf(pdf);
    }

    @Test
    void accessReviewSummary_noDecisions_generatesValidPdf() throws IOException {
        stubSettings();
        UUID campaignId = UUID.randomUUID();

        AccessReviewCampaign campaign = new AccessReviewCampaign();
        campaign.setId(campaignId);
        campaign.setName("Empty Campaign");
        campaign.setStatus(CampaignStatus.UPCOMING);
        campaign.setCreatedAt(OffsetDateTime.now());
        campaign.setDeadline(OffsetDateTime.now().plusDays(30));
        campaign.setReviewGroups(new ArrayList<>());

        when(campaignRepo.findById(campaignId)).thenReturn(Optional.of(campaign));

        byte[] pdf = service.generateAccessReviewSummary(campaignId);
        assertValidPdf(pdf);
    }

    @Test
    void accessReviewSummary_pendingDecisions_generatesValidPdf() throws IOException {
        stubSettings();
        UUID campaignId = UUID.randomUUID();

        AccessReviewCampaign campaign = new AccessReviewCampaign();
        campaign.setId(campaignId);
        campaign.setName("Pending Campaign");
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setCreatedAt(OffsetDateTime.now());
        campaign.setDeadline(OffsetDateTime.now().plusDays(14));

        Account reviewer = makeAccount(UUID.randomUUID(), "reviewer1", AccountRole.ADMIN);
        AccessReviewGroup group = new AccessReviewGroup();
        group.setGroupDn("cn=team,dc=example,dc=com");
        group.setGroupName("team");
        group.setReviewer(reviewer);
        group.setCampaign(campaign);

        // Decision with null decision = pending
        AccessReviewDecision pending = new AccessReviewDecision();
        pending.setMemberDn("uid=alice,dc=example,dc=com");
        pending.setMemberDisplay("Alice");
        pending.setDecision(null);
        pending.setReviewGroup(group);
        group.setDecisions(List.of(pending));

        campaign.setReviewGroups(List.of(group));
        when(campaignRepo.findById(campaignId)).thenReturn(Optional.of(campaign));

        byte[] pdf = service.generateAccessReviewSummary(campaignId);
        assertValidPdf(pdf);
    }

    @Test
    void accessReviewSummary_campaignNotFound_throws() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepo.findById(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateAccessReviewSummary(campaignId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Campaign not found");
    }

    // ── Privileged Account Inventory ───────────────────────────────────────

    @Test
    void privilegedAccountInventory_withAccounts_generatesValidPdf() throws IOException {
        stubSettings();

        UUID adminId = UUID.randomUUID();
        UUID superadminId = UUID.randomUUID();
        Account admin = makeAccount(adminId, "admin1", AccountRole.ADMIN);
        Account superadmin = makeAccount(superadminId, "superadmin1", AccountRole.SUPERADMIN);

        when(accountRepo.findAllByRole(AccountRole.ADMIN)).thenReturn(List.of(admin));
        when(accountRepo.findAllByRole(AccountRole.SUPERADMIN)).thenReturn(List.of(superadmin));

        // Profile roles for admin
        ProvisioningProfile profile = new ProvisioningProfile();
        profile.setName("Engineers");
        AdminProfileRole role = new AdminProfileRole();
        role.setProfile(profile);
        role.setBaseRole(BaseRole.ADMIN);
        when(profileRoleRepo.findAllByAdminAccountId(adminId)).thenReturn(List.of(role));
        when(profileRoleRepo.findAllByAdminAccountId(superadminId)).thenReturn(List.of());

        // Feature overrides
        AdminFeaturePermission perm = new AdminFeaturePermission();
        perm.setFeatureKey(FeatureKey.BULK_IMPORT);
        perm.setEnabled(false);
        when(featurePermRepo.findAllByAdminAccountId(adminId)).thenReturn(List.of(perm));
        when(featurePermRepo.findAllByAdminAccountId(superadminId)).thenReturn(List.of());

        byte[] pdf = service.generatePrivilegedAccountInventory();
        assertValidPdf(pdf);
    }

    @Test
    void privilegedAccountInventory_noAccounts_generatesValidPdf() throws IOException {
        stubSettings();

        when(accountRepo.findAllByRole(AccountRole.ADMIN)).thenReturn(List.of());
        when(accountRepo.findAllByRole(AccountRole.SUPERADMIN)).thenReturn(List.of());

        byte[] pdf = service.generatePrivilegedAccountInventory();
        assertValidPdf(pdf);
    }

    @Test
    void privilegedAccountInventory_accountWithNullFields_handlesGracefully() throws IOException {
        stubSettings();

        Account admin = new Account();
        admin.setId(UUID.randomUUID());
        admin.setUsername("sparse-admin");
        admin.setRole(AccountRole.ADMIN);
        admin.setAuthType(AccountType.LOCAL);
        // displayName, email, lastLoginAt are all null

        when(accountRepo.findAllByRole(AccountRole.ADMIN)).thenReturn(List.of(admin));
        when(accountRepo.findAllByRole(AccountRole.SUPERADMIN)).thenReturn(List.of());
        when(profileRoleRepo.findAllByAdminAccountId(admin.getId())).thenReturn(List.of());
        when(featurePermRepo.findAllByAdminAccountId(admin.getId())).thenReturn(List.of());

        byte[] pdf = service.generatePrivilegedAccountInventory();
        assertValidPdf(pdf);
    }

    // ── buildPdf helpers ───────────────────────────────────────────────────

    @Test
    void buildPdf_emptyRows_generatesValidPdf() throws IOException {
        stubSettings();
        byte[] pdf = service.buildPdf("Test Report", "subtitle",
                List.of("Col1", "Col2"), List.of());
        assertValidPdf(pdf);
    }

    @Test
    void buildPdf_multipleRows_generatesValidPdf() throws IOException {
        stubSettings();
        List<List<String>> rows = List.of(
                List.of("a", "b"),
                List.of("c", "d"),
                List.of("e", "f")
        );
        byte[] pdf = service.buildPdf("Test", "sub", List.of("H1", "H2"), rows);
        assertValidPdf(pdf);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private AccessReviewCampaign buildCampaign(UUID campaignId) {
        AccessReviewCampaign campaign = new AccessReviewCampaign();
        campaign.setId(campaignId);
        campaign.setName("Q1 2026 Review");
        campaign.setStatus(CampaignStatus.CLOSED);
        campaign.setCreatedAt(OffsetDateTime.now().minusDays(30));
        campaign.setDeadline(OffsetDateTime.now().minusDays(1));
        campaign.setCompletedAt(OffsetDateTime.now());

        Account reviewer = makeAccount(UUID.randomUUID(), "reviewer1", AccountRole.ADMIN);

        AccessReviewGroup group = new AccessReviewGroup();
        group.setGroupDn("cn=engineers,dc=example,dc=com");
        group.setGroupName("engineers");
        group.setReviewer(reviewer);
        group.setCampaign(campaign);

        AccessReviewDecision d1 = new AccessReviewDecision();
        d1.setMemberDn("uid=alice,dc=example,dc=com");
        d1.setMemberDisplay("Alice Smith");
        d1.setDecision(ReviewDecision.CONFIRM);
        d1.setDecidedBy(reviewer);
        d1.setDecidedAt(OffsetDateTime.now().minusDays(5));
        d1.setComment("Confirmed - active engineer");
        d1.setReviewGroup(group);

        AccessReviewDecision d2 = new AccessReviewDecision();
        d2.setMemberDn("uid=bob,dc=example,dc=com");
        d2.setMemberDisplay("Bob Jones");
        d2.setDecision(ReviewDecision.REVOKE);
        d2.setDecidedBy(reviewer);
        d2.setDecidedAt(OffsetDateTime.now().minusDays(3));
        d2.setComment("Left the team");
        d2.setReviewGroup(group);

        group.setDecisions(List.of(d1, d2));
        campaign.setReviewGroups(List.of(group));

        return campaign;
    }
}
