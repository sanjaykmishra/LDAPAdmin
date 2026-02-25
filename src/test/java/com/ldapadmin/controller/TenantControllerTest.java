package com.ldapadmin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.controller.superadmin.TenantController;
import com.ldapadmin.dto.tenant.TenantRequest;
import com.ldapadmin.dto.tenant.TenantResponse;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.service.TenantService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
class TenantControllerTest extends BaseControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TenantService tenantService;

    static final UUID TENANT_ID       = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID ADMIN_TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    TenantResponse sampleTenant() {
        return new TenantResponse(TENANT_ID, "Acme Corp", "acme", true,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    // ── GET /api/superadmin/tenants ───────────────────────────────────────────

    @Test
    void listTenants_superadmin_returns200WithList() throws Exception {
        given(tenantService.listTenants()).willReturn(List.of(sampleTenant()));

        mockMvc.perform(get("/api/superadmin/tenants")
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("acme"))
                .andExpect(jsonPath("$[0].id").value(TENANT_ID.toString()));
    }

    @Test
    void listTenants_adminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/superadmin/tenants")
                        .with(authentication(adminAuth(ADMIN_TENANT_ID))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTenants_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/superadmin/tenants"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/superadmin/tenants ──────────────────────────────────────────

    @Test
    void createTenant_superadmin_returns201() throws Exception {
        TenantRequest req = new TenantRequest("Acme Corp", "acme", true);
        given(tenantService.createTenant(any())).willReturn(sampleTenant());

        mockMvc.perform(post("/api/superadmin/tenants")
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.slug").value("acme"));
    }

    @Test
    void createTenant_duplicateSlug_returns409() throws Exception {
        TenantRequest req = new TenantRequest("Acme Corp", "acme", true);
        given(tenantService.createTenant(any()))
                .willThrow(new ConflictException("Slug already taken"));

        mockMvc.perform(post("/api/superadmin/tenants")
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void createTenant_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/superadmin/tenants")
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"slug\":\"acme\",\"enabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTenant_invalidSlugChars_returns400() throws Exception {
        mockMvc.perform(post("/api/superadmin/tenants")
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"slug\":\"UPPER_CASE\",\"enabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/superadmin/tenants/{id} ──────────────────────────────────────

    @Test
    void getTenant_superadmin_returns200() throws Exception {
        given(tenantService.getTenant(TENANT_ID)).willReturn(sampleTenant());

        mockMvc.perform(get("/api/superadmin/tenants/{id}", TENANT_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    @Test
    void getTenant_notFound_returns404() throws Exception {
        given(tenantService.getTenant(TENANT_ID))
                .willThrow(new ResourceNotFoundException("Tenant not found"));

        mockMvc.perform(get("/api/superadmin/tenants/{id}", TENANT_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/superadmin/tenants/{id} ──────────────────────────────────────

    @Test
    void updateTenant_superadmin_returns200() throws Exception {
        TenantRequest req = new TenantRequest("Acme Updated", "acme", true);
        TenantResponse updated = new TenantResponse(TENANT_ID, "Acme Updated", "acme", true,
                OffsetDateTime.now(), OffsetDateTime.now());
        given(tenantService.updateTenant(eq(TENANT_ID), any())).willReturn(updated);

        mockMvc.perform(put("/api/superadmin/tenants/{id}", TENANT_ID)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Updated"));
    }

    // ── DELETE /api/superadmin/tenants/{id} ───────────────────────────────────

    @Test
    void deleteTenant_superadmin_returns204() throws Exception {
        willDoNothing().given(tenantService).deleteTenant(TENANT_ID);

        mockMvc.perform(delete("/api/superadmin/tenants/{id}", TENANT_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTenant_notFound_returns404() throws Exception {
        willThrow(new ResourceNotFoundException("Tenant not found"))
                .given(tenantService).deleteTenant(TENANT_ID);

        mockMvc.perform(delete("/api/superadmin/tenants/{id}", TENANT_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isNotFound());
    }
}
