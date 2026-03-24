package com.ldapadmin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.AuthenticationService;
import com.ldapadmin.auth.LoginRateLimiter;
import com.ldapadmin.auth.OidcAuthenticationService;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.auth.dto.LoginRequest;
import com.ldapadmin.auth.dto.LoginResponse;
import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.ldap.LdapConnectionFactory;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.repository.AdminProfileRoleRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.ProvisioningProfileRepository;
import com.ldapadmin.service.ApplicationSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends BaseControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @MockBean AuthenticationService      authenticationService;
    @MockBean OidcAuthenticationService oidcAuthenticationService;
    @MockBean LoginRateLimiter          loginRateLimiter;
    @MockBean AdminProfileRoleRepository adminProfileRoleRepository;
    @MockBean ProvisioningProfileRepository provisioningProfileRepository;
    @MockBean DirectoryConnectionRepository directoryConnectionRepository;
    @MockBean LdapConnectionFactory ldapConnectionFactory;
    @MockBean LdapUserService ldapUserService;
    @MockBean ApplicationSettingsService applicationSettingsService;

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        LoginRequest req  = new LoginRequest("admin", "secret");
        LoginResponse res = new LoginResponse("jwt-token", "admin", "SUPERADMIN", null);
        given(authenticationService.login(any())).willReturn(res);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.accountType").value("SUPERADMIN"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        LoginRequest req = new LoginRequest("admin", "wrong");
        given(authenticationService.login(any())).willThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_authenticated_returnsUsernameAndType() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(PrincipalType.SUPERADMIN, ACCOUNT_ID, "alice");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.accountType").value("SUPERADMIN"))
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()));
    }

    @Test
    void me_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setupStatus_unauthenticated_returnsFalseByDefault() throws Exception {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setSetupCompleted(false);
        given(applicationSettingsService.getEntity()).willReturn(settings);

        mockMvc.perform(get("/api/v1/auth/setup-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupCompleted").value(false));
    }

    @Test
    void setupStatus_afterSetup_returnsTrue() throws Exception {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setSetupCompleted(true);
        given(applicationSettingsService.getEntity()).willReturn(settings);

        mockMvc.perform(get("/api/v1/auth/setup-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupCompleted").value(true));
    }
}
