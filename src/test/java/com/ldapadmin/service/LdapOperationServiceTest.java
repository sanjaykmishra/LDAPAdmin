package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PermissionService;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.ldap.AttributeModification;
import com.ldapadmin.dto.ldap.CreateEntryRequest;
import com.ldapadmin.dto.ldap.LdapEntryResponse;
import com.ldapadmin.dto.ldap.MoveUserRequest;
import com.ldapadmin.dto.ldap.UpdateEntryRequest;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapBrowseService;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapSchemaService;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LdapOperationServiceTest {

    @Mock private DirectoryConnectionRepository dirRepo;
    @Mock private PermissionService             permissionService;
    @Mock private LdapBrowseService             browseService;
    @Mock private LdapUserService               userService;
    @Mock private LdapGroupService              groupService;
    @Mock private LdapSchemaService             schemaService;
    @Mock private AuditService                  auditService;
    @Mock private BulkUserService               bulkUserService;
    @Mock private CsvMappingTemplateService     csvTemplateService;

    private LdapOperationService service;

    private final UUID dirId   = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LdapOperationService(
                dirRepo, permissionService, browseService, userService, groupService,
                schemaService, auditService, bulkUserService, csvTemplateService);
    }

    // ── Directory loading ─────────────────────────────────────────────────────

    @Test
    void searchUsers_directoryNotFound_throws() {
        when(dirRepo.findById(dirId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.searchUsers(dirId, adminPrincipal(), null, null, 100, new String[0]))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchUsers_disabledDirectory_throws() {
        DirectoryConnection dc = enabledDir(false);
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dc));

        assertThatThrownBy(() -> service.searchUsers(dirId, adminPrincipal(), null, null, 100, new String[0]))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchUsers_superadmin_noTenantScope() {
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dc));
        when(userService.searchUsers(eq(dc), anyString(), any(), anyInt(), any(String[].class))).thenReturn(List.of());

        List<LdapEntryResponse> result = service.searchUsers(dirId, superadminPrincipal(),
                "(cn=*)", null, 100, new String[0]);

        assertThat(result).isEmpty();
    }

    // ── User operations ───────────────────────────────────────────────────────

    @Test
    void deleteUser_callsUserService() {
        String dn = "cn=Bob,ou=Users,dc=example,dc=com";
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dc));

        service.deleteUser(dirId, adminPrincipal(), dn);

        verify(userService).deleteUser(dc, dn);
    }

    @Test
    void updateUser_convertsModificationsToUnboundId() {
        String dn = "cn=Dave,ou=Users,dc=example,dc=com";
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dc));
        LdapUser user = new LdapUser(dn, Map.of("mail", List.of("d@example.com")));
        when(userService.getUser(dc, dn)).thenReturn(user);

        UpdateEntryRequest req = new UpdateEntryRequest(List.of(
                new AttributeModification(AttributeModification.Operation.REPLACE,
                        "mail", List.of("dave@example.com"))));

        LdapEntryResponse resp = service.updateUser(dirId, adminPrincipal(), dn, req);

        verify(userService).updateUser(eq(dc), eq(dn), any());
        assertThat(resp.dn()).isEqualTo(dn);
    }

    // ── Group operations ──────────────────────────────────────────────────────

    @Test
    void addGroupMember_callsGroupService() {
        String groupDn = "cn=Staff,ou=Groups,dc=example,dc=com";
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dc));

        service.addGroupMember(dirId, adminPrincipal(), groupDn, "member", "cn=Alice,ou=Users");

        verify(groupService).addMember(dc, groupDn, "member", "cn=Alice,ou=Users");
    }

    @Test
    void searchUsers_limitIsPassedToUserService() {
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dc));

        when(userService.searchUsers(eq(dc), anyString(), any(), eq(2), any(String[].class)))
                .thenReturn(List.of(
                        new LdapUser("cn=A,dc=example,dc=com", Map.of()),
                        new LdapUser("cn=B,dc=example,dc=com", Map.of())));

        List<LdapEntryResponse> result = service.searchUsers(
                dirId, adminPrincipal(), null, null, 2, new String[0]);

        assertThat(result).hasSize(2);
        verify(userService).searchUsers(eq(dc), anyString(), any(), eq(2), any(String[].class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthPrincipal adminPrincipal() {
        return new AuthPrincipal(PrincipalType.ADMIN, adminId, "alice");
    }

    private AuthPrincipal superadminPrincipal() {
        return new AuthPrincipal(PrincipalType.SUPERADMIN, UUID.randomUUID(), "superadmin");
    }

    private DirectoryConnection enabledDir(boolean enabled) {
        DirectoryConnection dc = new DirectoryConnection();
        dc.setId(dirId);
        dc.setEnabled(enabled);
        dc.setDisplayName("test-dir");
        dc.setBaseDn("dc=example,dc=com");
        dc.setPagingSize(500);
        return dc;
    }
}
