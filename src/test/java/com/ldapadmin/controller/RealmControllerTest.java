package com.ldapadmin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.controller.directory.RealmController;
import com.ldapadmin.dto.realm.RealmRequest;
import com.ldapadmin.dto.realm.RealmResponse;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.service.RealmService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RealmController.class)
class RealmControllerTest extends BaseControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper mapper;

    @MockBean RealmService realmService;

    private static final UUID DIR_ID   = UUID.randomUUID();
    private static final UUID REALM_ID = UUID.randomUUID();

    // ── GET /api/v1/directories/{dirId}/realms ────────────────────────────────

    @Test
    void list_superadmin_returns200() throws Exception {
        when(realmService.listByDirectory(DIR_ID)).thenReturn(List.of(realmResponse()));

        mockMvc.perform(get("/api/v1/directories/{dirId}/realms", DIR_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Realm"));
    }

    @Test
    void list_admin_returns200() throws Exception {
        when(realmService.listByDirectory(DIR_ID)).thenReturn(List.of(realmResponse()));

        mockMvc.perform(get("/api/v1/directories/{dirId}/realms", DIR_ID)
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void list_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/directories/{dirId}/realms", DIR_ID))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/directories/{dirId}/realms ───────────────────────────────

    @Test
    void create_superadmin_returns201() throws Exception {
        RealmRequest req = validRequest();
        when(realmService.create(eq(DIR_ID), any(RealmRequest.class)))
                .thenReturn(realmResponse());

        mockMvc.perform(post("/api/v1/directories/{dirId}/realms", DIR_ID)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Realm"));
    }

    @Test
    void create_admin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/directories/{dirId}/realms", DIR_ID)
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_blankName_returns400() throws Exception {
        RealmRequest req = new RealmRequest("", "ou=users,dc=example,dc=com",
                "ou=groups,dc=example,dc=com", "inetOrgPerson", 0, null);

        mockMvc.perform(post("/api/v1/directories/{dirId}/realms", DIR_ID)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/directories/{dirId}/realms/{realmId} ─────────────────────

    @Test
    void get_superadmin_returns200() throws Exception {
        when(realmService.get(DIR_ID, REALM_ID)).thenReturn(realmResponse());

        mockMvc.perform(get("/api/v1/directories/{dirId}/realms/{realmId}", DIR_ID, REALM_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(realmService.get(DIR_ID, REALM_ID))
                .thenThrow(new ResourceNotFoundException("Realm", REALM_ID));

        mockMvc.perform(get("/api/v1/directories/{dirId}/realms/{realmId}", DIR_ID, REALM_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/v1/directories/{dirId}/realms/{realmId} ─────────────────────

    @Test
    void update_superadmin_returns200() throws Exception {
        when(realmService.update(eq(DIR_ID), eq(REALM_ID), any(RealmRequest.class)))
                .thenReturn(realmResponse());

        mockMvc.perform(put("/api/v1/directories/{dirId}/realms/{realmId}", DIR_ID, REALM_ID)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }

    // ── DELETE /api/v1/directories/{dirId}/realms/{realmId} ──────────────────

    @Test
    void delete_superadmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/directories/{dirId}/realms/{realmId}", DIR_ID, REALM_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Realm", REALM_ID))
                .when(realmService).delete(DIR_ID, REALM_ID);

        mockMvc.perform(delete("/api/v1/directories/{dirId}/realms/{realmId}", DIR_ID, REALM_ID)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RealmRequest validRequest() {
        return new RealmRequest("Test Realm",
                "ou=users,dc=example,dc=com",
                "ou=groups,dc=example,dc=com",
                "inetOrgPerson", 0, null);
    }

    private RealmResponse realmResponse() {
        return new RealmResponse(REALM_ID, DIR_ID, "Test Realm",
                "ou=users,dc=example,dc=com",
                "ou=groups,dc=example,dc=com",
                "inetOrgPerson", 0, List.of(),
                OffsetDateTime.now(), OffsetDateTime.now());
    }
}
