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
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapSchemaService;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LdapOperationServiceTest {

    @Mock private DirectoryConnectionRepository dirRepo;
    @Mock private PermissionService             permissionService;
    @Mock private LdapUserService               userService;
    @Mock private LdapGroupService              groupService;
    @Mock private LdapSchemaService             schemaService;

    private LdapOperationService service;

    private final UUID dirId    = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID adminId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LdapOperationService(
                dirRepo, permissionService, userService, groupService, schemaService);
    }

    // ── Directory loading ─────────────────────────────────────────────────────

    @Test
    void searchUsers_directoryNotFound_throws() {
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.searchUsers(dirId, adminPrincipal(), null, null, 100, new String[0]))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchUsers_disabledDirectory_throws() {
        DirectoryConnection dc = enabledDir(false);
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dc));

        assertThatThrownBy(() -> service.searchUsers(dirId, adminPrincipal(), null, null, 100, new String[0]))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchUsers_superadmin_noTenantScope() {
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findById(dirId)).thenReturn(Optional.of(dc));
        when(userService.searchUsers(eq(dc), anyString(), any(), any())).thenReturn(List.of());

        List<LdapEntryResponse> result = service.searchUsers(dirId, superadminPrincipal(),
                "(cn=*)", null, 100, new String[0]);

        assertThat(result).isEmpty();
        verify(dirRepo, never()).findByIdAndTenantId(any(), any());
    }

    // ── User operations ───────────────────────────────────────────────────────

    @Test
    void getUser_callsBranchAccessCheck() {
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dc));
        LdapUser user = new LdapUser("cn=Alice,ou=Users,dc=example,dc=com",
                Map.of("cn", List.of("Alice")));
        when(userService.getUser(eq(dc), anyString(), any())).thenReturn(user);

        AuthPrincipal principal = adminPrincipal();
        service.getUser(dirId, principal, "cn=Alice,ou=Users,dc=example,dc=com", new String[0]);

        verify(permissionService).requireBranchAccess(
                principal, dirId, "cn=Alice,ou=Users,dc=example,dc=com");
    }

    @Test
    void createUser_branchDenied_doesNotCallLdap() {
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dc));
        AuthPrincipal principal = adminPrincipal();
        doThrow(new AccessDeniedException("branch denied"))
                .when(permissionService).requireBranchAccess(principal, dirId, "cn=X,ou=Restricted");

        CreateEntryRequest req = new CreateEntryRequest(
                "cn=X,ou=Restricted", Map.of("cn", List.of("X")));

        assertThatThrownBy(() -> service.createUser(dirId, principal, req))
                .isInstanceOf(AccessDeniedException.class);

        verify(userService, never()).createUser(any(), anyString(), any());
    }

    @Test
    void deleteUser_callsUserService() {
        String dn = "cn=Bob,ou=Users,dc=example,dc=com";
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dc));

        service.deleteUser(dirId, adminPrincipal(), dn);

        verify(userService).deleteUser(dc, dn);
    }

    @Test
    void moveUser_checksTargetBranchAccess() {
        String dn        = "cn=Carol,ou=Old,dc=example,dc=com";
        String newParent = "ou=New,dc=example,dc=com";
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dc));
        AuthPrincipal principal = adminPrincipal();

        service.moveUser(dirId, principal, dn, new MoveUserRequest(newParent));

        verify(permissionService).requireBranchAccess(principal, dirId, dn);
        verify(permissionService).requireBranchAccess(principal, dirId, newParent);
        verify(userService).moveUser(dc, dn, newParent);
    }

    @Test
    void updateUser_convertsModificationsToUnboundId() {
        String dn = "cn=Dave,ou=Users,dc=example,dc=com";
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dc));
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
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dc));

        service.addGroupMember(dirId, adminPrincipal(), groupDn, "member", "cn=Alice,ou=Users");

        verify(groupService).addMember(dc, groupDn, "member", "cn=Alice,ou=Users");
    }

    @Test
    void searchUsers_limitIsRespected() {
        DirectoryConnection dc = enabledDir(true);
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dc));

        List<LdapUser> bigList = List.of(
                new LdapUser("cn=A,dc=example,dc=com", Map.of()),
                new LdapUser("cn=B,dc=example,dc=com", Map.of()),
                new LdapUser("cn=C,dc=example,dc=com", Map.of()));
        when(userService.searchUsers(eq(dc), anyString(), any(), any())).thenReturn(bigList);

        List<LdapEntryResponse> result = service.searchUsers(
                dirId, adminPrincipal(), null, null, 2, new String[0]);

        assertThat(result).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthPrincipal adminPrincipal() {
        return new AuthPrincipal(PrincipalType.ADMIN, adminId, tenantId, "alice");
    }

    private AuthPrincipal superadminPrincipal() {
        return new AuthPrincipal(PrincipalType.SUPERADMIN, UUID.randomUUID(), null, "superadmin");
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
