package com.ldapadmin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.dto.settings.ApplicationSettingsDto;
import com.ldapadmin.dto.settings.BrandingDto;
import com.ldapadmin.dto.settings.UpdateApplicationSettingsRequest;
import com.ldapadmin.service.ApplicationSettingsService;
import com.ldapadmin.service.siem.SiemExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicationSettingsController.class)
class ApplicationSettingsControllerTest extends BaseControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ApplicationSettingsService settingsService;
    @MockBean SiemExportService          siemExportService;

    static final String BASE_URL = "/api/v1/settings";

    ApplicationSettingsDto sampleSettings() {
        return new ApplicationSettingsDto(
                UUID.randomUUID(),
                "LDAPAdmin", null, null, null,
                false,
                30,
                null, null, null, null, false, false,
                null, null, null, false, null, 24,
                null,
                null, null, null, false, null, null, false, null, null,
                null, null, false, null, null,
                // SIEM
                false, null, null, null, null, false, null, false,
                // Setup wizard
                false,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    BrandingDto sampleBranding() {
        return new BrandingDto("LDAPAdmin", null, null, null, null);
    }

    UpdateApplicationSettingsRequest sampleUpdateRequest() {
        return new UpdateApplicationSettingsRequest(
                "LDAPAdmin", null, null, null,
                false,
                30,
                null, null, null, null, null, false,
                null, null, null, null, null, 1,
                null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                // SIEM
                null, null, null, null, null, null, null, null,
                // Setup wizard
                null);
    }

    // ── GET /api/v1/settings ─────────────────────────────────────────────────

    @Test
    void getSettings_authenticated_returns200() throws Exception {
        given(settingsService.get()).willReturn(sampleSettings());

        mockMvc.perform(get(BASE_URL).with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("LDAPAdmin"));
    }

    @Test
    void getSettings_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSettings_returnsSiemFields() throws Exception {
        ApplicationSettingsDto withSiem = new ApplicationSettingsDto(
                UUID.randomUUID(),
                "LDAPAdmin", null, null, null,
                false, 30,
                null, null, null, null, false, false,
                null, null, null, false, null, 24,
                null,
                null, null, null, false, null, null, false, null, null,
                null, null, false, null, null,
                // SIEM
                true, com.ldapadmin.entity.enums.SiemProtocol.SYSLOG_UDP,
                "siem.corp.com", 514, com.ldapadmin.entity.enums.SiemFormat.CEF,
                true, null, false,
                // Setup wizard
                false,
                OffsetDateTime.now(), OffsetDateTime.now());

        given(settingsService.get()).willReturn(withSiem);

        mockMvc.perform(get(BASE_URL).with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siemEnabled").value(true))
                .andExpect(jsonPath("$.siemProtocol").value("SYSLOG_UDP"))
                .andExpect(jsonPath("$.siemHost").value("siem.corp.com"))
                .andExpect(jsonPath("$.siemFormat").value("CEF"))
                .andExpect(jsonPath("$.siemAuthTokenConfigured").value(true));
    }

    // ── GET /api/v1/settings/branding ────────────────────────────────────────

    @Test
    void getBranding_unauthenticated_returns200() throws Exception {
        given(settingsService.getBranding()).willReturn(sampleBranding());

        mockMvc.perform(get(BASE_URL + "/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("LDAPAdmin"));
    }

    // ── PUT /api/v1/settings ─────────────────────────────────────────────────

    @Test
    void upsertSettings_authenticated_returns200() throws Exception {
        given(settingsService.upsert(any())).willReturn(sampleSettings());

        mockMvc.perform(put(BASE_URL)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleUpdateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("LDAPAdmin"));
    }

    // ── POST /api/v1/settings/siem/test ──────────────────────────────────────

    @Test
    void testSiem_authenticated_returns200() throws Exception {
        given(siemExportService.testConnectivity()).willReturn("TCP: Connected");
        given(siemExportService.sendTestEvent()).willReturn("Test event sent successfully.");

        mockMvc.perform(post(BASE_URL + "/siem/test")
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectivity").value("TCP: Connected"))
                .andExpect(jsonPath("$.delivery").value("Test event sent successfully."));
    }

    @Test
    void testSiem_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE_URL + "/siem/test"))
                .andExpect(status().isUnauthorized());
    }
}
