package com.ldapadmin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.controller.directory.AuditorLinkController;
import com.ldapadmin.dto.auditor.AuditorLinkDto;
import com.ldapadmin.dto.auditor.CreateAuditorLinkRequest;
import com.ldapadmin.service.AuditorLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditorLinkController.class)
class AuditorLinkControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuditorLinkService auditorLinkService;

    private final UUID dirId = UUID.randomUUID();

    private static final String BASE_URL = "/api/v1/directories/{dirId}/auditor-links";

    // ── POST (create) ─────────────────────────────────────────────────────────

    @Test
    void create_superadmin_returns201WithDto() throws Exception {
        UUID linkId = UUID.randomUUID();
        AuditorLinkDto dto = new AuditorLinkDto(
                linkId, dirId, "test-token", "Q1 Audit",
                List.of(), true, false, true,
                null, null, OffsetDateTime.now().plusDays(30),
                "admin", OffsetDateTime.now(), null, 0, false, null);

        when(auditorLinkService.create(eq(dirId), any(CreateAuditorLinkRequest.class), any()))
                .thenReturn(dto);

        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                "Q1 Audit", List.of(), true, false, true, null, null, 30);

        mvc.perform(post(BASE_URL, dirId)
                        .with(authentication(superadminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(linkId.toString()))
                .andExpect(jsonPath("$.token").value("test-token"))
                .andExpect(jsonPath("$.label").value("Q1 Audit"))
                .andExpect(jsonPath("$.directoryId").value(dirId.toString()));

        verify(auditorLinkService).create(eq(dirId), any(CreateAuditorLinkRequest.class), any());
    }

    @Test
    void create_admin_returns201() throws Exception {
        AuditorLinkDto dto = new AuditorLinkDto(
                UUID.randomUUID(), dirId, "token", "Audit",
                List.of(), true, false, true,
                null, null, OffsetDateTime.now().plusDays(30),
                "admin", OffsetDateTime.now(), null, 0, false, null);

        when(auditorLinkService.create(eq(dirId), any(), any())).thenReturn(dto);

        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                "Audit", List.of(), true, false, true, null, null, 30);

        mvc.perform(post(BASE_URL, dirId)
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                "Audit", List.of(), true, false, true, null, null, 30);

        mvc.perform(post(BASE_URL, dirId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_invalidExpiryDays_returns400() throws Exception {
        // expiryDays = 0, which violates @Min(1)
        String json = """
                {"label":"Test","campaignIds":[],"includeSod":true,
                 "includeEntitlements":false,"includeAuditEvents":true,
                 "expiryDays":0}""";

        mvc.perform(post(BASE_URL, dirId)
                        .with(authentication(superadminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_expiryDaysExceedsMax_returns400() throws Exception {
        String json = """
                {"label":"Test","campaignIds":[],"includeSod":true,
                 "includeEntitlements":false,"includeAuditEvents":true,
                 "expiryDays":999}""";

        mvc.perform(post(BASE_URL, dirId)
                        .with(authentication(superadminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_nullCampaignIds_returns400() throws Exception {
        String json = """
                {"label":"Test","includeSod":true,
                 "includeEntitlements":false,"includeAuditEvents":true,
                 "expiryDays":30}""";

        mvc.perform(post(BASE_URL, dirId)
                        .with(authentication(superadminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    // ── GET (list) ────────────────────────────────────────────────────────────

    @Test
    void list_superadmin_returns200WithLinks() throws Exception {
        AuditorLinkDto dto = new AuditorLinkDto(
                UUID.randomUUID(), dirId, "token", "Q1 Audit",
                List.of(), true, false, true,
                null, null, OffsetDateTime.now().plusDays(30),
                "admin", OffsetDateTime.now(), null, 5, false, null);

        when(auditorLinkService.list(dirId)).thenReturn(List.of(dto));

        mvc.perform(get(BASE_URL, dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label").value("Q1 Audit"))
                .andExpect(jsonPath("$[0].accessCount").value(5));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mvc.perform(get(BASE_URL, dirId))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE (revoke) ───────────────────────────────────────────────────────

    @Test
    void revoke_superadmin_returns204() throws Exception {
        UUID linkId = UUID.randomUUID();

        mvc.perform(delete(BASE_URL + "/{linkId}", dirId, linkId)
                        .with(authentication(superadminAuth()))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(auditorLinkService).revoke(eq(linkId), any());
    }

    @Test
    void revoke_admin_returns204() throws Exception {
        UUID linkId = UUID.randomUUID();

        mvc.perform(delete(BASE_URL + "/{linkId}", dirId, linkId)
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(auditorLinkService).revoke(eq(linkId), any());
    }

    @Test
    void revoke_unauthenticated_returns401() throws Exception {
        UUID linkId = UUID.randomUUID();

        mvc.perform(delete(BASE_URL + "/{linkId}", dirId, linkId))
                .andExpect(status().isUnauthorized());
    }
}
