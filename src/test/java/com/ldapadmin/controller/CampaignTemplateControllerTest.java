package com.ldapadmin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.controller.directory.CampaignTemplateController;
import com.ldapadmin.dto.accessreview.*;
import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.service.AccessReviewCampaignService;
import com.ldapadmin.service.CampaignTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CampaignTemplateController.class)
class CampaignTemplateControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @MockBean private CampaignTemplateService templateService;
    @MockBean private AccessReviewCampaignService campaignService;

    private final UUID dirId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID campaignId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();

    // ── Create ─────────────────────────────────────────────────────────────

    @Test
    void create_asSuperadmin_returns201() throws Exception {
        when(templateService.create(eq(dirId), any(), any())).thenReturn(buildResponse());

        mvc.perform(post("/api/v1/directories/{dirId}/campaign-templates", dirId)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Template"));
    }

    @Test
    void create_asNonSuperadmin_returns403() throws Exception {
        mvc.perform(post("/api/v1/directories/{dirId}/campaign-templates", dirId)
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_invalidRequest_returns400() throws Exception {
        var body = """
                {"name": "", "deadlineDays": 0, "groups": []}
                """;

        mvc.perform(post("/api/v1/directories/{dirId}/campaign-templates", dirId)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── List ───────────────────────────────────────────────────────────────

    @Test
    void list_returns200() throws Exception {
        when(templateService.list(dirId)).thenReturn(List.of(buildResponse()));

        mvc.perform(get("/api/v1/directories/{dirId}/campaign-templates", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Template"))
                .andExpect(jsonPath("$[0].config.deadlineDays").value(30));
    }

    @Test
    void list_empty_returns200WithEmptyArray() throws Exception {
        when(templateService.list(dirId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/directories/{dirId}/campaign-templates", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── Get ────────────────────────────────────────────────────────────────

    @Test
    void get_returns200() throws Exception {
        when(templateService.get(dirId, templateId)).thenReturn(buildResponse());

        mvc.perform(get("/api/v1/directories/{dirId}/campaign-templates/{templateId}", dirId, templateId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(templateId.toString()))
                .andExpect(jsonPath("$.config.groups[0].groupDn").value("cn=admins,dc=test"));
    }

    // ── Update ─────────────────────────────────────────────────────────────

    @Test
    void update_asSuperadmin_returns200() throws Exception {
        var updated = new CampaignTemplateResponse(
                templateId, "Updated Template", "Updated desc", dirId,
                new CampaignTemplateResponse.CampaignTemplateConfigDto(
                        60, 6, true, true,
                        List.of(new CampaignTemplateResponse.GroupConfigDto("cn=new,dc=test", "member", reviewerId))),
                "admin", OffsetDateTime.now(), OffsetDateTime.now());
        when(templateService.update(eq(dirId), eq(templateId), any(), any())).thenReturn(updated);

        var body = """
                {
                  "name": "Updated Template",
                  "description": "Updated desc",
                  "deadlineDays": 60,
                  "recurrenceMonths": 6,
                  "autoRevoke": true,
                  "autoRevokeOnExpiry": true,
                  "groups": [{"groupDn": "cn=new,dc=test", "memberAttribute": "member", "reviewerAccountId": "%s"}]
                }
                """.formatted(reviewerId);

        mvc.perform(put("/api/v1/directories/{dirId}/campaign-templates/{templateId}", dirId, templateId)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Template"))
                .andExpect(jsonPath("$.config.deadlineDays").value(60));
    }

    @Test
    void update_asNonSuperadmin_returns403() throws Exception {
        var body = """
                {
                  "name": "Updated",
                  "deadlineDays": 30,
                  "groups": [{"groupDn": "cn=test,dc=test", "memberAttribute": "member", "reviewerAccountId": "%s"}]
                }
                """.formatted(reviewerId);

        mvc.perform(put("/api/v1/directories/{dirId}/campaign-templates/{templateId}", dirId, templateId)
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── Delete ─────────────────────────────────────────────────────────────

    @Test
    void delete_asSuperadmin_returns204() throws Exception {
        mvc.perform(delete("/api/v1/directories/{dirId}/campaign-templates/{templateId}", dirId, templateId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isNoContent());

        verify(templateService).delete(dirId, templateId);
    }

    @Test
    void delete_asNonSuperadmin_returns403() throws Exception {
        mvc.perform(delete("/api/v1/directories/{dirId}/campaign-templates/{templateId}", dirId, templateId)
                        .with(authentication(adminAuth())))
                .andExpect(status().isForbidden());
    }

    // ── Create Campaign from Template ──────────────────────────────────────

    @Test
    void createCampaignFromTemplate_returns201() throws Exception {
        var campaignReq = new CreateCampaignRequest(
                "Test Template", "desc", 30, null, false, false,
                List.of(new CreateCampaignRequest.GroupAssignment("cn=admins,dc=test", "member", reviewerId)));
        when(templateService.toCampaignRequest(eq(dirId), eq(templateId), any(), any())).thenReturn(campaignReq);

        var campaign = new AccessReviewCampaign();
        campaign.setId(campaignId);
        when(campaignService.create(eq(dirId), any(), any())).thenReturn(campaign);
        when(campaignService.get(dirId, campaignId)).thenReturn(
                new CampaignDetailDto(campaignId, "Test Template", "desc", CampaignStatus.UPCOMING,
                        null, OffsetDateTime.now().plusDays(30), 30, null, false, false,
                        OffsetDateTime.now(), null, "admin",
                        new CampaignProgressDto(0, 0, 0, 0, 0), List.of(), List.of()));

        mvc.perform(post("/api/v1/directories/{dirId}/campaign-templates/{templateId}/create-campaign", dirId, templateId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Template"));
    }

    @Test
    void createCampaignFromTemplate_withOverrides_returns201() throws Exception {
        var campaignReq = new CreateCampaignRequest(
                "Custom Name", "Custom Desc", 30, null, false, false,
                List.of(new CreateCampaignRequest.GroupAssignment("cn=admins,dc=test", "member", reviewerId)));
        when(templateService.toCampaignRequest(eq(dirId), eq(templateId), eq("Custom Name"), eq("Custom Desc")))
                .thenReturn(campaignReq);

        var campaign = new AccessReviewCampaign();
        campaign.setId(campaignId);
        when(campaignService.create(eq(dirId), any(), any())).thenReturn(campaign);
        when(campaignService.get(dirId, campaignId)).thenReturn(
                new CampaignDetailDto(campaignId, "Custom Name", "Custom Desc", CampaignStatus.UPCOMING,
                        null, OffsetDateTime.now().plusDays(30), 30, null, false, false,
                        OffsetDateTime.now(), null, "admin",
                        new CampaignProgressDto(0, 0, 0, 0, 0), List.of(), List.of()));

        mvc.perform(post("/api/v1/directories/{dirId}/campaign-templates/{templateId}/create-campaign", dirId, templateId)
                        .param("name", "Custom Name")
                        .param("description", "Custom Desc")
                        .with(authentication(superadminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Custom Name"));
    }

    // ── Save as Template ───────────────────────────────────────────────────

    @Test
    void saveAsTemplate_returns201() throws Exception {
        when(templateService.createFromCampaign(eq(dirId), eq(campaignId), any())).thenReturn(buildResponse());

        mvc.perform(post("/api/v1/directories/{dirId}/campaign-templates/from-campaign/{campaignId}", dirId, campaignId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Template"));
    }

    @Test
    void saveAsTemplate_asNonSuperadmin_returns403() throws Exception {
        mvc.perform(post("/api/v1/directories/{dirId}/campaign-templates/from-campaign/{campaignId}", dirId, campaignId)
                        .with(authentication(adminAuth())))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private CampaignTemplateResponse buildResponse() {
        return new CampaignTemplateResponse(
                templateId, "Test Template", "Test desc", dirId,
                new CampaignTemplateResponse.CampaignTemplateConfigDto(
                        30, null, false, false,
                        List.of(new CampaignTemplateResponse.GroupConfigDto("cn=admins,dc=test", "member", reviewerId))),
                "admin", OffsetDateTime.now(), OffsetDateTime.now());
    }

    private String createRequestBody() {
        return """
                {
                  "name": "Test Template",
                  "description": "Test desc",
                  "deadlineDays": 30,
                  "autoRevoke": false,
                  "autoRevokeOnExpiry": false,
                  "groups": [{"groupDn": "cn=admins,dc=test", "memberAttribute": "member", "reviewerAccountId": "%s"}]
                }
                """.formatted(reviewerId);
    }
}
