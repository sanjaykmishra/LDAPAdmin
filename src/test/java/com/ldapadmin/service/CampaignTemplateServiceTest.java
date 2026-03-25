package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.accessreview.CampaignTemplateResponse;
import com.ldapadmin.dto.accessreview.CreateCampaignRequest;
import com.ldapadmin.dto.accessreview.CreateCampaignTemplateRequest;
import com.ldapadmin.dto.accessreview.UpdateCampaignTemplateRequest;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.exception.LdapAdminException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.CampaignTemplateRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignTemplateServiceTest {

    @Mock private CampaignTemplateRepository templateRepo;
    @Mock private DirectoryConnectionRepository directoryRepo;
    @Mock private AccountRepository accountRepo;
    @Mock private AccessReviewCampaignRepository campaignRepo;
    @Mock private AuditService auditService;

    private CampaignTemplateService service;

    private final UUID directoryId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final AuthPrincipal principal = new AuthPrincipal(PrincipalType.SUPERADMIN, adminId, "admin");

    private DirectoryConnection directory;
    private Account adminAccount;
    private Account reviewerAccount;

    @BeforeEach
    void setUp() {
        service = new CampaignTemplateService(templateRepo, directoryRepo, accountRepo, campaignRepo, auditService);

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Directory");

        adminAccount = new Account();
        adminAccount.setId(adminId);
        adminAccount.setUsername("admin");

        reviewerAccount = new Account();
        reviewerAccount.setId(reviewerId);
        reviewerAccount.setUsername("reviewer");

        // Stub reviewer resolution for toResponse and toCampaignRequest
        lenient().when(accountRepo.findById(reviewerId)).thenReturn(Optional.of(reviewerAccount));
        lenient().when(accountRepo.existsById(reviewerId)).thenReturn(true);
    }

    // ── create ─────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_createsTemplate() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(templateRepo.save(any())).thenAnswer(inv -> {
            CampaignTemplate t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(OffsetDateTime.now());
            t.setUpdatedAt(OffsetDateTime.now());
            return t;
        });

        var req = buildCreateRequest();
        CampaignTemplateResponse result = service.create(directoryId, req, principal);

        assertThat(result.name()).isEqualTo("Quarterly Template");
        assertThat(result.config().deadlineDays()).isEqualTo(30);
        assertThat(result.config().groups()).hasSize(1);
        assertThat(result.createdByUsername()).isEqualTo("admin");
        verify(templateRepo).save(any());
    }

    @Test
    void create_invalidDeadline_throwsException() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));

        var req = new CreateCampaignTemplateRequest(
                "Bad Template", null, 0, null, false, false,
                List.of(new CreateCampaignTemplateRequest.GroupConfig("cn=test,dc=test", "member", reviewerId)));

        assertThatThrownBy(() -> service.create(directoryId, req, principal))
                .isInstanceOf(LdapAdminException.class)
                .hasMessageContaining("at least 1 day");
    }

    @Test
    void create_invalidRecurrence_throwsException() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));

        var req = new CreateCampaignTemplateRequest(
                "Bad Template", null, 30, 0, false, false,
                List.of(new CreateCampaignTemplateRequest.GroupConfig("cn=test,dc=test", "member", reviewerId)));

        assertThatThrownBy(() -> service.create(directoryId, req, principal))
                .isInstanceOf(LdapAdminException.class)
                .hasMessageContaining("at least 1 month");
    }

    @Test
    void create_directoryNotFound_throwsException() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.empty());

        var req = buildCreateRequest();

        assertThatThrownBy(() -> service.create(directoryId, req, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_accountNotFound_throwsException() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.empty());

        var req = buildCreateRequest();

        assertThatThrownBy(() -> service.create(directoryId, req, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── update ─────────────────────────────────────────────────────────────

    @Test
    void update_validRequest_updatesTemplate() {
        CampaignTemplate existing = buildTemplate();
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(existing));
        when(templateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpdateCampaignTemplateRequest(
                "Updated Name", "Updated desc", 60, 6, true, true,
                List.of(new CreateCampaignTemplateRequest.GroupConfig("cn=new,dc=test", "uniqueMember", reviewerId)));

        CampaignTemplateResponse result = service.update(directoryId, templateId, req, principal);

        assertThat(result.name()).isEqualTo("Updated Name");
        assertThat(result.config().deadlineDays()).isEqualTo(60);
        assertThat(result.config().recurrenceMonths()).isEqualTo(6);
        assertThat(result.config().autoRevoke()).isTrue();
    }

    @Test
    void update_templateNotFound_throwsException() {
        when(templateRepo.findById(templateId)).thenReturn(Optional.empty());

        var req = new UpdateCampaignTemplateRequest(
                "Updated", null, 30, null, false, false,
                List.of(new CreateCampaignTemplateRequest.GroupConfig("cn=test,dc=test", "member", reviewerId)));

        assertThatThrownBy(() -> service.update(directoryId, templateId, req, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_wrongDirectory_throwsException() {
        CampaignTemplate existing = buildTemplate();
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(existing));

        UUID otherDirId = UUID.randomUUID();
        var req = new UpdateCampaignTemplateRequest(
                "Updated", null, 30, null, false, false,
                List.of(new CreateCampaignTemplateRequest.GroupConfig("cn=test,dc=test", "member", reviewerId)));

        assertThatThrownBy(() -> service.update(otherDirId, templateId, req, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── list ───────────────────────────────────────────────────────────────

    @Test
    void list_returnsTemplatesForDirectory() {
        CampaignTemplate t1 = buildTemplate();
        CampaignTemplate t2 = buildTemplate();
        t2.setId(UUID.randomUUID());
        t2.setName("Another Template");
        when(templateRepo.findByDirectoryIdOrderByCreatedAtDesc(directoryId))
                .thenReturn(List.of(t1, t2));

        List<CampaignTemplateResponse> result = service.list(directoryId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Test Template");
        assertThat(result.get(1).name()).isEqualTo("Another Template");
    }

    @Test
    void list_emptyDirectory_returnsEmptyList() {
        when(templateRepo.findByDirectoryIdOrderByCreatedAtDesc(directoryId))
                .thenReturn(List.of());

        List<CampaignTemplateResponse> result = service.list(directoryId);

        assertThat(result).isEmpty();
    }

    // ── get ────────────────────────────────────────────────────────────────

    @Test
    void get_existingTemplate_returnsResponse() {
        CampaignTemplate existing = buildTemplate();
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(existing));

        CampaignTemplateResponse result = service.get(directoryId, templateId);

        assertThat(result.id()).isEqualTo(templateId);
        assertThat(result.name()).isEqualTo("Test Template");
        assertThat(result.directoryId()).isEqualTo(directoryId);
    }

    @Test
    void get_notFound_throwsException() {
        when(templateRepo.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(directoryId, templateId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_wrongDirectory_throwsException() {
        CampaignTemplate existing = buildTemplate();
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(existing));

        UUID otherDirId = UUID.randomUUID();
        assertThatThrownBy(() -> service.get(otherDirId, templateId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ─────────────────────────────────────────────────────────────

    @Test
    void delete_existingTemplate_deletesSuccessfully() {
        CampaignTemplate existing = buildTemplate();
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(existing));

        service.delete(directoryId, templateId, principal);

        verify(templateRepo).delete(existing);
    }

    @Test
    void delete_notFound_throwsException() {
        when(templateRepo.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(directoryId, templateId, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_wrongDirectory_throwsException() {
        CampaignTemplate existing = buildTemplate();
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(existing));

        UUID otherDirId = UUID.randomUUID();
        assertThatThrownBy(() -> service.delete(otherDirId, templateId, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── createFromCampaign ─────────────────────────────────────────────────

    @Test
    void createFromCampaign_extractsConfigFromCampaign() {
        AccessReviewCampaign campaign = buildCampaign();
        when(campaignRepo.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(templateRepo.save(any())).thenAnswer(inv -> {
            CampaignTemplate t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(OffsetDateTime.now());
            t.setUpdatedAt(OffsetDateTime.now());
            return t;
        });

        CampaignTemplateResponse result = service.createFromCampaign(directoryId, campaign.getId(), principal);

        assertThat(result.name()).isEqualTo("Test Campaign (Template)");
        assertThat(result.config().deadlineDays()).isEqualTo(30);
        assertThat(result.config().autoRevoke()).isTrue();
        assertThat(result.config().groups()).hasSize(1);
        assertThat(result.config().groups().get(0).groupDn()).isEqualTo("cn=admins,dc=test");
    }

    @Test
    void createFromCampaign_campaignNotFound_throwsException() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepo.findById(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFromCampaign(directoryId, campaignId, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createFromCampaign_wrongDirectory_throwsException() {
        AccessReviewCampaign campaign = buildCampaign();
        when(campaignRepo.findById(campaign.getId())).thenReturn(Optional.of(campaign));

        UUID otherDirId = UUID.randomUUID();
        assertThatThrownBy(() -> service.createFromCampaign(otherDirId, campaign.getId(), principal))
                .isInstanceOf(LdapAdminException.class)
                .hasMessageContaining("does not belong");
    }

    // ── toCampaignRequest ──────────────────────────────────────────────────

    @Test
    void toCampaignRequest_convertsTemplateToRequest() {
        CampaignTemplate existing = buildTemplate();
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(existing));

        CreateCampaignRequest result = service.toCampaignRequest(directoryId, templateId, null, null);

        assertThat(result.name()).isEqualTo("Test Template");
        assertThat(result.deadlineDays()).isEqualTo(30);
        assertThat(result.groups()).hasSize(1);
        assertThat(result.groups().get(0).groupDn()).isEqualTo("cn=admins,dc=test");
        assertThat(result.groups().get(0).reviewerAccountId()).isEqualTo(reviewerId);
    }

    @Test
    void toCampaignRequest_withOverrides_usesOverrideValues() {
        CampaignTemplate existing = buildTemplate();
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(existing));

        CreateCampaignRequest result = service.toCampaignRequest(directoryId, templateId, "Custom Name", "Custom Desc");

        assertThat(result.name()).isEqualTo("Custom Name");
        assertThat(result.description()).isEqualTo("Custom Desc");
    }

    @Test
    void toCampaignRequest_blankNameOverride_usesTemplateName() {
        CampaignTemplate existing = buildTemplate();
        when(templateRepo.findById(templateId)).thenReturn(Optional.of(existing));

        CreateCampaignRequest result = service.toCampaignRequest(directoryId, templateId, "  ", null);

        assertThat(result.name()).isEqualTo("Test Template");
    }

    @Test
    void toCampaignRequest_templateNotFound_throwsException() {
        when(templateRepo.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toCampaignRequest(directoryId, templateId, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Response mapping ───────────────────────────────────────────────────

    @Test
    void create_responseIncludesAllFields() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(templateRepo.save(any())).thenAnswer(inv -> {
            CampaignTemplate t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(OffsetDateTime.now());
            t.setUpdatedAt(OffsetDateTime.now());
            return t;
        });

        var req = new CreateCampaignTemplateRequest(
                "Full Template", "Full desc", 45, 3, true, true,
                List.of(new CreateCampaignTemplateRequest.GroupConfig("cn=group1,dc=test", "uniqueMember", reviewerId)));

        CampaignTemplateResponse result = service.create(directoryId, req, principal);

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("Full Template");
        assertThat(result.description()).isEqualTo("Full desc");
        assertThat(result.directoryId()).isEqualTo(directoryId);
        assertThat(result.config().deadlineDays()).isEqualTo(45);
        assertThat(result.config().recurrenceMonths()).isEqualTo(3);
        assertThat(result.config().autoRevoke()).isTrue();
        assertThat(result.config().autoRevokeOnExpiry()).isTrue();
        assertThat(result.config().groups()).hasSize(1);
        assertThat(result.config().groups().get(0).memberAttribute()).isEqualTo("uniqueMember");
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private CreateCampaignTemplateRequest buildCreateRequest() {
        return new CreateCampaignTemplateRequest(
                "Quarterly Template", "Quarterly access review",
                30, 3, false, false,
                List.of(new CreateCampaignTemplateRequest.GroupConfig(
                        "cn=admins,dc=test", "member", reviewerId)));
    }

    private CampaignTemplate buildTemplate() {
        CampaignTemplate t = new CampaignTemplate();
        t.setId(templateId);
        t.setDirectory(directory);
        t.setName("Test Template");
        t.setDescription("Test description");
        t.setCreatedBy(adminAccount);
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());

        CampaignTemplateConfig config = new CampaignTemplateConfig(
                30, null, false, false,
                List.of(new CampaignTemplateConfig.GroupConfig("cn=admins,dc=test", "member", reviewerId)));
        t.setConfig(config);

        return t;
    }

    private AccessReviewCampaign buildCampaign() {
        AccessReviewCampaign c = new AccessReviewCampaign();
        c.setId(UUID.randomUUID());
        c.setDirectory(directory);
        c.setName("Test Campaign");
        c.setDescription("Campaign desc");
        c.setStatus(CampaignStatus.ACTIVE);
        c.setDeadline(OffsetDateTime.now().plusDays(30));
        c.setDeadlineDays(30);
        c.setAutoRevoke(true);
        c.setAutoRevokeOnExpiry(false);
        c.setRecurrenceMonths(3);
        c.setCreatedBy(adminAccount);

        AccessReviewGroup g = new AccessReviewGroup();
        g.setId(UUID.randomUUID());
        g.setCampaign(c);
        g.setGroupDn("cn=admins,dc=test");
        g.setGroupName("admins");
        g.setMemberAttribute("member");
        g.setReviewer(reviewerAccount);
        c.setReviewGroups(new ArrayList<>(List.of(g)));

        return c;
    }
}
