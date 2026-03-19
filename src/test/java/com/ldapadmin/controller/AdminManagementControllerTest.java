package com.ldapadmin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.controller.superadmin.AdminManagementController;
import com.ldapadmin.dto.admin.AdminAccountRequest;
import com.ldapadmin.dto.admin.AdminAccountResponse;
import com.ldapadmin.dto.admin.AdminPermissionsResponse;
import com.ldapadmin.dto.admin.RealmRoleRequest;
import com.ldapadmin.dto.admin.RealmRoleResponse;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.entity.enums.BaseRole;
import com.ldapadmin.service.AdminManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminManagementController.class)
class AdminManagementControllerTest extends BaseControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AdminManagementService service;

    static final UUID ADMIN_ID  = UUID.fromString("30000000-0000-0000-0000-000000000003");
    static final UUID REALM_ID  = UUID.fromString("40000000-0000-0000-0000-000000000004");
    static final String BASE_URL = "/api/v1/superadmin/admins";

    AdminAccountResponse sampleResponse() {
        return new AdminAccountResponse(
                ADMIN_ID, "testadmin", "Test Admin", "testadmin@example.com",
                AccountRole.ADMIN, AccountType.LOCAL, null, true,
                null, Instant.now(), Instant.now());
    }

    AdminAccountRequest sampleRequest() {
        return new AdminAccountRequest(
                "testadmin", "Test Admin", "testadmin@example.com",
                AccountRole.ADMIN, AccountType.LOCAL, "password123", null, true);
    }

    // ── GET /api/v1/superadmin/admins ────────────────────────────────────────

    @Test
    void listAdmins_superadmin_returns200() throws Exception {
        given(service.listAdmins()).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get(BASE_URL).with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("testadmin"));
    }

    @Test
    void listAdmins_admin_returns403() throws Exception {
        mockMvc.perform(get(BASE_URL).with(authentication(adminAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAdmins_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/superadmin/admins ───────────────────────────────────────

    @Test
    void createAdmin_superadmin_returns201() throws Exception {
        given(service.createAdmin(any())).willReturn(sampleResponse());

        mockMvc.perform(post(BASE_URL)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("testadmin"));
    }

    // ── GET /api/v1/superadmin/admins/{id} ───────────────────────────────────

    @Test
    void getAdmin_superadmin_returns200() throws Exception {
        given(service.getAdmin(eq(ADMIN_ID))).willReturn(sampleResponse());

        mockMvc.perform(get(BASE_URL + "/" + ADMIN_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.username").value("testadmin"));
    }

    // ── PUT /api/v1/superadmin/admins/{id} ───────────────────────────────────

    @Test
    void updateAdmin_superadmin_returns200() throws Exception {
        given(service.updateAdmin(eq(ADMIN_ID), any())).willReturn(sampleResponse());

        mockMvc.perform(put(BASE_URL + "/" + ADMIN_ID)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testadmin"));
    }

    // ── DELETE /api/v1/superadmin/admins/{id} ────────────────────────────────

    @Test
    void deleteAdmin_superadmin_returns204() throws Exception {
        willDoNothing().given(service).deleteAdmin(eq(ADMIN_ID));

        mockMvc.perform(delete(BASE_URL + "/" + ADMIN_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/v1/superadmin/admins/{id}/permissions ───────────────────────

    @Test
    void getPermissions_superadmin_returns200() throws Exception {
        given(service.getPermissions(eq(ADMIN_ID)))
                .willReturn(new AdminPermissionsResponse(List.of(), List.of()));

        mockMvc.perform(get(BASE_URL + "/" + ADMIN_ID + "/permissions")
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realmRoles").isArray())
                .andExpect(jsonPath("$.featurePermissions").isArray());
    }

    // ── PUT /api/v1/superadmin/admins/{id}/permissions/realm-roles ───────────

    @Test
    void assignRealmRole_superadmin_returns200() throws Exception {
        RealmRoleRequest req = new RealmRoleRequest(REALM_ID, BaseRole.ADMIN);
        RealmRoleResponse resp = new RealmRoleResponse(
                UUID.randomUUID(), REALM_ID, "Test Realm", BaseRole.ADMIN);
        given(service.assignRealmRole(eq(ADMIN_ID), any())).willReturn(resp);

        mockMvc.perform(put(BASE_URL + "/" + ADMIN_ID + "/permissions/realm-roles")
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realmId").value(REALM_ID.toString()))
                .andExpect(jsonPath("$.baseRole").value("ADMIN"));
    }
}
