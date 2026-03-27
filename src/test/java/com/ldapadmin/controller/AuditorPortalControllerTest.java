package com.ldapadmin.controller;

import com.ldapadmin.auth.IpRateLimiter;
import com.ldapadmin.dto.settings.BrandingDto;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.AuditorLink;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.exception.TooManyRequestsException;
import com.ldapadmin.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditorPortalController.class)
class AuditorPortalControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private AuditorLinkService auditorLinkService;
    @MockBean private AuditorPortalService portalService;
    @MockBean private ApplicationSettingsService settingsService;
    @MockBean private EvidencePackageService evidencePackageService;
    @MockBean private IpRateLimiter ipRateLimiter;

    private static final String TOKEN = "valid-test-token";
    private static final String BASE = "/api/v1/auditor/" + TOKEN;

    private AuditorLink link;

    @BeforeEach
    void setUp() {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(UUID.randomUUID());
        dir.setDisplayName("Corporate LDAP");

        Account creator = new Account();
        creator.setUsername("admin");

        link = AuditorLink.builder()
                .directory(dir)
                .token(TOKEN)
                .label("Q1 SOC 2 Audit")
                .campaignIds(List.of(UUID.randomUUID()))
                .includeSod(true)
                .includeEntitlements(true)
                .includeAuditEvents(true)
                .dataFrom(OffsetDateTime.now().minusDays(90))
                .dataTo(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .hmacSignature("test-hmac")
                .createdBy(creator)
                .build();
        link.setId(UUID.randomUUID());
        link.setCreatedAt(OffsetDateTime.now());

        when(auditorLinkService.validateToken(eq(TOKEN), any(), any())).thenReturn(link);
        when(settingsService.getBranding()).thenReturn(
                new BrandingDto("LDAPAdmin", null, "#3b82f6", null, Set.of(AccountType.LOCAL)));
    }

    // ── No auth required ──────────────────────────────────────────────────

    @Test
    void metadata_noAuth_returns200() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Q1 SOC 2 Audit"))
                .andExpect(jsonPath("$.directoryName").value("Corporate LDAP"))
                .andExpect(jsonPath("$.branding.appName").value("LDAPAdmin"))
                .andExpect(jsonPath("$.scope.includeSod").value(true))
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    // ── Invalid / expired / revoked tokens → 404 ──────────────────────────

    @Test
    void metadata_invalidToken_returns404() throws Exception {
        when(auditorLinkService.validateToken(eq("bad-token"), any(), any()))
                .thenThrow(new ResourceNotFoundException("Auditor link not found"));

        mvc.perform(get("/api/v1/auditor/bad-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void metadata_expiredToken_returns404() throws Exception {
        when(auditorLinkService.validateToken(eq("expired-token"), any(), any()))
                .thenThrow(new ResourceNotFoundException("Auditor link not found"));

        mvc.perform(get("/api/v1/auditor/expired-token"))
                .andExpect(status().isNotFound());
    }

    // ── Rate limiting ─────────────────────────────────────────────────────

    @Test
    void metadata_rateLimited_returns429() throws Exception {
        doThrow(new TooManyRequestsException("Rate limit exceeded"))
                .when(ipRateLimiter).check(any());

        mvc.perform(get(BASE))
                .andExpect(status().isTooManyRequests());
    }

    // ── Campaigns ─────────────────────────────────────────────────────────

    @Test
    void campaigns_returns200WithData() throws Exception {
        when(portalService.getCampaigns(any())).thenReturn(List.of(
                Map.of("id", "c1", "name", "Q1 Review", "status", "CLOSED")));

        mvc.perform(get(BASE + "/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Q1 Review"));
    }

    @Test
    void campaignDetail_returns200() throws Exception {
        UUID campaignId = link.getCampaignIds().get(0);
        when(portalService.getCampaignDetail(any(), eq(campaignId)))
                .thenReturn(Map.of("id", campaignId.toString(), "name", "Q1 Review"));

        mvc.perform(get(BASE + "/campaigns/" + campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Q1 Review"));
    }

    @Test
    void campaignDetail_notInScope_returns404() throws Exception {
        UUID outOfScope = UUID.randomUUID();
        when(portalService.getCampaignDetail(any(), eq(outOfScope))).thenReturn(null);

        mvc.perform(get(BASE + "/campaigns/" + outOfScope))
                .andExpect(status().isNotFound());
    }

    // ── SoD ───────────────────────────────────────────────────────────────

    @Test
    void sod_returns200() throws Exception {
        when(portalService.getSodData(any()))
                .thenReturn(Map.of("policies", List.of(), "violations", List.of()));

        mvc.perform(get(BASE + "/sod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies").isArray());
    }

    @Test
    void sod_notIncluded_returns404() throws Exception {
        link.setIncludeSod(false);

        mvc.perform(get(BASE + "/sod"))
                .andExpect(status().isNotFound());
    }

    // ── Entitlements ──────────────────────────────────────────────────────

    @Test
    void entitlements_returns200() throws Exception {
        when(portalService.getEntitlements(any())).thenReturn(List.of());

        mvc.perform(get(BASE + "/entitlements"))
                .andExpect(status().isOk());
    }

    @Test
    void entitlements_notIncluded_returns404() throws Exception {
        link.setIncludeEntitlements(false);

        mvc.perform(get(BASE + "/entitlements"))
                .andExpect(status().isNotFound());
    }

    // ── Audit events ──────────────────────────────────────────────────────

    @Test
    void auditEvents_returns200() throws Exception {
        when(portalService.getAuditEvents(any())).thenReturn(List.of());

        mvc.perform(get(BASE + "/audit-events"))
                .andExpect(status().isOk());
    }

    @Test
    void auditEvents_notIncluded_returns404() throws Exception {
        link.setIncludeAuditEvents(false);

        mvc.perform(get(BASE + "/audit-events"))
                .andExpect(status().isNotFound());
    }

    // ── Approvals ─────────────────────────────────────────────────────────

    @Test
    void approvals_returns200() throws Exception {
        when(portalService.getApprovals(any())).thenReturn(List.of());

        mvc.perform(get(BASE + "/approvals"))
                .andExpect(status().isOk());
    }

    // ── Verify ────────────────────────────────────────────────────────────

    @Test
    void verify_returnsVerifiedTrue() throws Exception {
        mvc.perform(get(BASE + "/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.tokenValid").value(true));
    }

    // ── Export ─────────────────────────────────────────────────────────────

    @Test
    void export_returnsZip() throws Exception {
        when(evidencePackageService.generateEvidencePackage(any(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), any())).thenReturn("fake-zip".getBytes());

        mvc.perform(get(BASE + "/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("evidence-package.zip")))
                .andExpect(header().string("X-Robots-Tag", "noindex"));
    }

    // ── Security: auditor routes don't require auth ───────────────────────

    @Test
    void allEndpoints_noAuthRequired_noCsrfRequired() throws Exception {
        // Verify portal endpoints work without any authentication token
        when(portalService.getCampaigns(any())).thenReturn(List.of());
        when(portalService.getApprovals(any())).thenReturn(List.of());

        mvc.perform(get(BASE + "/campaigns")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/approvals")).andExpect(status().isOk());
    }

    @Test
    void securedEndpoints_stillRequireAuth_afterPermitAllChange() throws Exception {
        // Verify that the SecurityConfig permitAll for /api/v1/auditor/**
        // does NOT accidentally open other /api/v1/ endpoints
        mvc.perform(get("/api/v1/directories/" + UUID.randomUUID() + "/auditor-links"))
                .andExpect(status().isUnauthorized());
    }
}
