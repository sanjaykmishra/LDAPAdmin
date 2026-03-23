package com.ldapadmin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.controller.directory.AccessReviewController;
import com.ldapadmin.dto.accessreview.*;
import com.ldapadmin.dto.admin.AdminAccountResponse;
import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.ReviewDecision;
import com.ldapadmin.service.AccessReviewCampaignService;
import com.ldapadmin.service.AccessReviewDecisionService;
import com.ldapadmin.service.AdminManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccessReviewController.class)
class AccessReviewControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @MockBean private AccessReviewCampaignService campaignService;
    @MockBean private AccessReviewDecisionService decisionService;
    @MockBean private AdminManagementService adminService;

    private final UUID dirId = UUID.randomUUID();
    private final UUID campaignId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    @Test
    void listCampaigns_returns200() throws Exception {
        var summary = new CampaignSummaryDto(campaignId, "Q1 Review", CampaignStatus.UPCOMING,
                null, OffsetDateTime.now().plusDays(30), 30, null, OffsetDateTime.now(), "admin",
                new CampaignProgressDto(0, 0, 0, 0, 0));
        when(campaignService.list(eq(dirId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Q1 Review"));
    }

    @Test
    void listReviewers_returns200() throws Exception {
        var reviewer = new AdminAccountResponse(UUID.randomUUID(), "reviewer1", "Reviewer One",
                "r@test.com", AccountRole.ADMIN, AccountType.LOCAL, null, true, null, null, null);
        when(adminService.listAdminsByDirectory(dirId)).thenReturn(List.of(reviewer));

        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/reviewers", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("reviewer1"));
    }

    @Test
    void createCampaign_asSuperadmin_returns201() throws Exception {
        var campaign = new AccessReviewCampaign();
        campaign.setId(campaignId);
        when(campaignService.create(eq(dirId), any(), any())).thenReturn(campaign);
        when(campaignService.get(campaignId)).thenReturn(
                new CampaignDetailDto(campaignId, "Q1 Review", "Test", CampaignStatus.UPCOMING,
                        null, OffsetDateTime.now().plusDays(30), 30, null, false, false,
                        OffsetDateTime.now(), null, "admin",
                        new CampaignProgressDto(0, 0, 0, 0, 0), List.of(), List.of()));

        var body = """
                {
                  "name": "Q1 Review",
                  "description": "Test",
                  "deadlineDays": 30,
                  "autoRevoke": false,
                  "autoRevokeOnExpiry": false,
                  "groups": [{"groupDn": "cn=admins,dc=test", "memberAttribute": "member", "reviewerAccountId": "%s"}]
                }
                """.formatted(UUID.randomUUID());

        mvc.perform(post("/api/v1/directories/{dirId}/access-reviews", dirId)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Q1 Review"));
    }

    @Test
    void createCampaign_asNonSuperadmin_returns403() throws Exception {
        var body = """
                {
                  "name": "Q1 Review",
                  "deadlineDays": 30,
                  "groups": [{"groupDn": "cn=admins,dc=test", "memberAttribute": "member", "reviewerAccountId": "%s"}]
                }
                """.formatted(UUID.randomUUID());

        mvc.perform(post("/api/v1/directories/{dirId}/access-reviews", dirId)
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCampaign_withRecurrence_returns201() throws Exception {
        var campaign = new AccessReviewCampaign();
        campaign.setId(campaignId);
        when(campaignService.create(eq(dirId), any(), any())).thenReturn(campaign);
        when(campaignService.get(campaignId)).thenReturn(
                new CampaignDetailDto(campaignId, "Quarterly Review", null, CampaignStatus.UPCOMING,
                        null, OffsetDateTime.now().plusDays(30), 30, 3, false, false,
                        OffsetDateTime.now(), null, "admin",
                        new CampaignProgressDto(0, 0, 0, 0, 0), List.of(), List.of()));

        var body = """
                {
                  "name": "Quarterly Review",
                  "deadlineDays": 30,
                  "recurrenceMonths": 3,
                  "autoRevoke": false,
                  "autoRevokeOnExpiry": false,
                  "groups": [{"groupDn": "cn=admins,dc=test", "memberAttribute": "member", "reviewerAccountId": "%s"}]
                }
                """.formatted(UUID.randomUUID());

        mvc.perform(post("/api/v1/directories/{dirId}/access-reviews", dirId)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrenceMonths").value(3))
                .andExpect(jsonPath("$.deadlineDays").value(30));
    }

    @Test
    void getCampaign_returns200() throws Exception {
        when(campaignService.get(campaignId)).thenReturn(
                new CampaignDetailDto(campaignId, "Q1 Review", null, CampaignStatus.ACTIVE,
                        null, OffsetDateTime.now().plusDays(30), 30, null, false, false,
                        OffsetDateTime.now(), null, "admin",
                        new CampaignProgressDto(10, 5, 2, 3, 70.0), List.of(), List.of()));

        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/{campaignId}", dirId, campaignId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.progress.total").value(10));
    }

    @Test
    void listDecisions_returns200() throws Exception {
        UUID decisionId = UUID.randomUUID();
        when(decisionService.listForReviewGroup(eq(groupId), any())).thenReturn(List.of(
                new DecisionDto(decisionId, "uid=user1,dc=test", "User One",
                        null, null, null, null, null)));

        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/{campaignId}/groups/{groupId}/decisions",
                        dirId, campaignId, groupId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberDn").value("uid=user1,dc=test"));
    }

    @Test
    void submitDecision_returns200() throws Exception {
        UUID decisionId = UUID.randomUUID();
        when(decisionService.decide(eq(decisionId), eq(ReviewDecision.CONFIRM), any(), any()))
                .thenReturn(new DecisionDto(decisionId, "uid=user1,dc=test", "User One",
                        ReviewDecision.CONFIRM, "OK", "admin", OffsetDateTime.now(), null));

        mvc.perform(post("/api/v1/directories/{dirId}/access-reviews/{campaignId}/groups/{groupId}/decisions/{decisionId}",
                        dirId, campaignId, groupId, decisionId)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "CONFIRM", "comment": "OK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("CONFIRM"));
    }

    @Test
    void exportCsv_returns200WithCsvContentType() throws Exception {
        when(campaignService.exportCsv(campaignId)).thenReturn("header\nrow1\n".getBytes());

        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/{campaignId}/export", dirId, campaignId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"));
    }
}
