package com.ldapadmin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.controller.directory.UserController;
import com.ldapadmin.dto.ldap.AttributeModification;
import com.ldapadmin.dto.ldap.CreateEntryRequest;
import com.ldapadmin.dto.ldap.LdapEntryResponse;
import com.ldapadmin.dto.ldap.MoveUserRequest;
import com.ldapadmin.dto.ldap.UpdateEntryRequest;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.service.LdapOperationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest extends BaseControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean LdapOperationService ldapService;

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID DIR_ID    = UUID.fromString("20000000-0000-0000-0000-000000000002");
    static final String BASE_URL = "/api/directories/" + DIR_ID + "/users";
    static final String ENTRY_DN = "uid=alice,ou=people,dc=example,dc=com";

    LdapEntryResponse sampleEntry() {
        return new LdapEntryResponse(ENTRY_DN, Map.of(
                "cn",   List.of("Alice"),
                "mail", List.of("alice@example.com")));
    }

    // ── GET /search ───────────────────────────────────────────────────────────

    @Test
    void searchUsers_authenticated_returns200() throws Exception {
        given(ldapService.searchUsers(eq(DIR_ID), any(), isNull(), isNull(), anyInt(), any()))
                .willReturn(List.of(sampleEntry()));

        mockMvc.perform(get(BASE_URL).with(authentication(adminAuth(TENANT_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dn").value(ENTRY_DN));
    }

    @Test
    void searchUsers_withFilter_returns200() throws Exception {
        given(ldapService.searchUsers(eq(DIR_ID), any(), anyString(), isNull(), anyInt(), any()))
                .willReturn(List.of(sampleEntry()));

        mockMvc.perform(get(BASE_URL)
                        .param("filter", "(cn=alice)")
                        .with(authentication(adminAuth(TENANT_ID))))
                .andExpect(status().isOk());
    }

    @Test
    void searchUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /create ──────────────────────────────────────────────────────────

    @Test
    void createUser_admin_returns201() throws Exception {
        CreateEntryRequest req = new CreateEntryRequest(ENTRY_DN,
                Map.of("cn", List.of("Alice"), "sn", List.of("Smith")));
        given(ldapService.createUser(eq(DIR_ID), any(), any())).willReturn(sampleEntry());

        mockMvc.perform(post(BASE_URL)
                        .with(authentication(adminAuth(TENANT_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dn").value(ENTRY_DN));
    }

    // ── GET /entry ────────────────────────────────────────────────────────────

    @Test
    void getUser_byDn_returns200() throws Exception {
        given(ldapService.getUser(eq(DIR_ID), any(), eq(ENTRY_DN), any()))
                .willReturn(sampleEntry());

        mockMvc.perform(get(BASE_URL + "/entry")
                        .param("dn", ENTRY_DN)
                        .with(authentication(adminAuth(TENANT_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dn").value(ENTRY_DN))
                .andExpect(jsonPath("$.attributes.cn[0]").value("Alice"));
    }

    @Test
    void getUser_notFound_returns404() throws Exception {
        given(ldapService.getUser(eq(DIR_ID), any(), eq(ENTRY_DN), any()))
                .willThrow(new ResourceNotFoundException("Entry not found"));

        mockMvc.perform(get(BASE_URL + "/entry")
                        .param("dn", ENTRY_DN)
                        .with(authentication(adminAuth(TENANT_ID))))
                .andExpect(status().isNotFound());
    }

    // ── PUT /entry ────────────────────────────────────────────────────────────

    @Test
    void updateUser_admin_returns200() throws Exception {
        UpdateEntryRequest req = new UpdateEntryRequest(
                List.of(new AttributeModification(AttributeModification.Operation.REPLACE,
                        "mail", List.of("newalice@example.com"))));
        given(ldapService.updateUser(eq(DIR_ID), any(), eq(ENTRY_DN), any()))
                .willReturn(sampleEntry());

        mockMvc.perform(put(BASE_URL + "/entry")
                        .param("dn", ENTRY_DN)
                        .with(authentication(adminAuth(TENANT_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ── DELETE /entry ─────────────────────────────────────────────────────────

    @Test
    void deleteUser_admin_returns204() throws Exception {
        willDoNothing().given(ldapService).deleteUser(eq(DIR_ID), any(), eq(ENTRY_DN));

        mockMvc.perform(delete(BASE_URL + "/entry")
                        .param("dn", ENTRY_DN)
                        .with(authentication(adminAuth(TENANT_ID))))
                .andExpect(status().isNoContent());
    }

    // ── POST /enable & /disable ───────────────────────────────────────────────

    @Test
    void enableUser_admin_returns204() throws Exception {
        willDoNothing().given(ldapService).enableUser(eq(DIR_ID), any(), eq(ENTRY_DN));

        mockMvc.perform(post(BASE_URL + "/enable")
                        .param("dn", ENTRY_DN)
                        .with(authentication(adminAuth(TENANT_ID))))
                .andExpect(status().isNoContent());
    }

    @Test
    void disableUser_admin_returns204() throws Exception {
        willDoNothing().given(ldapService).disableUser(eq(DIR_ID), any(), eq(ENTRY_DN));

        mockMvc.perform(post(BASE_URL + "/disable")
                        .param("dn", ENTRY_DN)
                        .with(authentication(adminAuth(TENANT_ID))))
                .andExpect(status().isNoContent());
    }

    // ── POST /move ────────────────────────────────────────────────────────────

    @Test
    void moveUser_admin_returns204() throws Exception {
        MoveUserRequest req = new MoveUserRequest("ou=staff,dc=example,dc=com");
        willDoNothing().given(ldapService).moveUser(eq(DIR_ID), any(), eq(ENTRY_DN), any());

        mockMvc.perform(post(BASE_URL + "/move")
                        .param("dn", ENTRY_DN)
                        .with(authentication(adminAuth(TENANT_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }
}
