package com.ldapadmin.auth;

import com.ldapadmin.auth.dto.LoginRequest;
import com.ldapadmin.auth.dto.LoginResponse;
import com.ldapadmin.entity.AdminAccount;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.SuperadminAccount;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.entity.TenantAuthConfig;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.entity.enums.AuthType;
import com.ldapadmin.entity.enums.SslMode;
import com.ldapadmin.ldap.LdapConnectionFactory;
import com.ldapadmin.repository.AdminAccountRepository;
import com.ldapadmin.repository.SuperadminAccountRepository;
import com.ldapadmin.repository.TenantAuthConfigRepository;
import com.ldapadmin.repository.TenantRepository;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private SuperadminAccountRepository superadminRepo;
    @Mock private AdminAccountRepository      adminRepo;
    @Mock private TenantRepository            tenantRepo;
    @Mock private TenantAuthConfigRepository  authConfigRepo;
    @Mock private LdapConnectionFactory       connectionFactory;
    @Mock private JwtTokenService             jwtTokenService;
    @Mock private PasswordEncoder             passwordEncoder;

    private AuthenticationService authService;

    private InMemoryDirectoryServer ldapServer;

    private final UUID superadminId = UUID.randomUUID();
    private final UUID tenantId     = UUID.randomUUID();
    private final UUID adminId      = UUID.randomUUID();

    // LDAP credentials registered with the in-memory server
    private static final String LDAP_BIND_DN = "uid=ldap-admin,dc=example,dc=com";
    private static final String LDAP_PASSWORD = "ldap-secret";

    @BeforeEach
    void setUp() throws Exception {
        authService = new AuthenticationService(
                superadminRepo, adminRepo, tenantRepo, authConfigRepo,
                connectionFactory, jwtTokenService, passwordEncoder);

        InMemoryDirectoryServerConfig cfg =
                new InMemoryDirectoryServerConfig("dc=example,dc=com");
        cfg.addAdditionalBindCredentials(LDAP_BIND_DN, LDAP_PASSWORD);
        ldapServer = new InMemoryDirectoryServer(cfg);
        ldapServer.startListening();

        lenient().when(jwtTokenService.issue(any())).thenReturn("token-abc");
    }

    @AfterEach
    void tearDown() {
        if (ldapServer != null) ldapServer.shutDown(true);
    }

    // ── LOCAL superadmin ──────────────────────────────────────────────────────

    @Test
    void login_localSuperadmin_correctPassword_returnsJwt() {
        SuperadminAccount sa = localSuperadmin("alice", "$2a$bcrypt");
        when(superadminRepo.findByUsernameAndActiveTrue("alice")).thenReturn(Optional.of(sa));
        when(passwordEncoder.matches("secret", "$2a$bcrypt")).thenReturn(true);

        LoginResponse resp = authService.login(new LoginRequest("alice", "secret", null));

        assertThat(resp.token()).isEqualTo("token-abc");
        assertThat(resp.username()).isEqualTo("alice");
        assertThat(resp.accountType()).isEqualTo("SUPERADMIN");
        assertThat(sa.getLastLoginAt()).isNotNull();
    }

    @Test
    void login_localSuperadmin_wrongPassword_throwsBadCredentials() {
        SuperadminAccount sa = localSuperadmin("alice", "$2a$bcrypt");
        when(superadminRepo.findByUsernameAndActiveTrue("alice")).thenReturn(Optional.of(sa));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong", null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_localSuperadmin_notFound_throwsBadCredentials() {
        when(superadminRepo.findByUsernameAndActiveTrue("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "pw", null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_localSuperadmin_blankTenantSlug_treatedAsSuperadminLogin() {
        SuperadminAccount sa = localSuperadmin("root", "$2a$hash");
        when(superadminRepo.findByUsernameAndActiveTrue("root")).thenReturn(Optional.of(sa));
        when(passwordEncoder.matches("pw", "$2a$hash")).thenReturn(true);

        // blank (not null) slug → superadmin path
        LoginResponse resp = authService.login(new LoginRequest("root", "pw", "   "));
        assertThat(resp.accountType()).isEqualTo("SUPERADMIN");
    }

    // ── LDAP-sourced superadmin ───────────────────────────────────────────────

    @Test
    void login_ldapSuperadmin_correctPassword_returnsJwt() throws Exception {
        DirectoryConnection dir = ldapDirectoryConnection();
        SuperadminAccount sa = ldapSuperadmin("ldap-alice", LDAP_BIND_DN, dir);
        when(superadminRepo.findByUsernameAndActiveTrue("ldap-alice")).thenReturn(Optional.of(sa));
        when(connectionFactory.openUnboundConnection(dir)).thenReturn(ldapServer.getConnection());

        LoginResponse resp = authService.login(new LoginRequest("ldap-alice", LDAP_PASSWORD, null));

        assertThat(resp.token()).isEqualTo("token-abc");
        assertThat(resp.accountType()).isEqualTo("SUPERADMIN");
    }

    @Test
    void login_ldapSuperadmin_wrongPassword_throwsBadCredentials() throws Exception {
        DirectoryConnection dir = ldapDirectoryConnection();
        SuperadminAccount sa = ldapSuperadmin("ldap-alice", LDAP_BIND_DN, dir);
        when(superadminRepo.findByUsernameAndActiveTrue("ldap-alice")).thenReturn(Optional.of(sa));
        when(connectionFactory.openUnboundConnection(dir)).thenReturn(ldapServer.getConnection());

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("ldap-alice", "wrong-pw", null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_ldapSuperadmin_nullDirectory_throwsBadCredentials() {
        SuperadminAccount sa = ldapSuperadmin("ldap-alice", LDAP_BIND_DN, null);
        when(superadminRepo.findByUsernameAndActiveTrue("ldap-alice")).thenReturn(Optional.of(sa));

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("ldap-alice", "pw", null)))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(connectionFactory);
    }

    // ── Admin login (tenant-scoped) ───────────────────────────────────────────

    @Test
    void login_admin_ldapBind_success_returnsJwt() throws Exception {
        Tenant tenant = tenant("acme");
        AdminAccount admin = adminAccount(tenant, "bob");
        DirectoryConnection dir = ldapDirectoryConnection();
        TenantAuthConfig cfg = authConfig(AuthType.LDAP_BIND, dir, LDAP_BIND_DN.replace("ldap-admin", "{username}"));
        // pattern uid={username},dc=example,dc=com → uid=bob,dc=example,dc=com
        // but our in-memory bind dn is uid=ldap-admin,... so let's match it exactly
        // pattern: LDAP_BIND_DN template
        TenantAuthConfig cfgExact = authConfig(AuthType.LDAP_BIND, dir, LDAP_BIND_DN);

        when(tenantRepo.findBySlugAndEnabledTrue("acme")).thenReturn(Optional.of(tenant));
        when(adminRepo.findByTenantIdAndUsernameAndActiveTrue(tenantId, "bob"))
                .thenReturn(Optional.of(admin));
        when(authConfigRepo.findByTenantId(tenantId)).thenReturn(Optional.of(cfgExact));
        when(connectionFactory.openUnboundConnection(dir)).thenReturn(ldapServer.getConnection());

        LoginResponse resp = authService.login(new LoginRequest("bob", LDAP_PASSWORD, "acme"));

        assertThat(resp.accountType()).isEqualTo("ADMIN");
        assertThat(admin.getLastLoginAt()).isNotNull();
    }

    @Test
    void login_admin_samlTenant_throwsBadCredentials() {
        Tenant tenant = tenant("saml-corp");
        AdminAccount admin = adminAccount(tenant, "carol");
        TenantAuthConfig cfg = new TenantAuthConfig();
        cfg.setAuthType(AuthType.SAML);

        when(tenantRepo.findBySlugAndEnabledTrue("saml-corp")).thenReturn(Optional.of(tenant));
        when(adminRepo.findByTenantIdAndUsernameAndActiveTrue(tenantId, "carol"))
                .thenReturn(Optional.of(admin));
        when(authConfigRepo.findByTenantId(tenantId)).thenReturn(Optional.of(cfg));

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("carol", "pw", "saml-corp")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("SSO");
    }

    @Test
    void login_admin_tenantNotFound_throwsBadCredentials() {
        when(tenantRepo.findBySlugAndEnabledTrue("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("user", "pw", "unknown")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_admin_userNotActive_throwsBadCredentials() {
        Tenant tenant = tenant("acme");
        when(tenantRepo.findBySlugAndEnabledTrue("acme")).thenReturn(Optional.of(tenant));
        when(adminRepo.findByTenantIdAndUsernameAndActiveTrue(tenantId, "inactive"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("inactive", "pw", "acme")))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SuperadminAccount localSuperadmin(String username, String hash) {
        SuperadminAccount sa = new SuperadminAccount();
        sa.setId(superadminId);
        sa.setUsername(username);
        sa.setAccountType(AccountType.LOCAL);
        sa.setPasswordHash(hash);
        sa.setActive(true);
        return sa;
    }

    private SuperadminAccount ldapSuperadmin(String username, String ldapDn,
                                              DirectoryConnection dir) {
        SuperadminAccount sa = new SuperadminAccount();
        sa.setId(superadminId);
        sa.setUsername(username);
        sa.setAccountType(AccountType.LDAP);
        sa.setLdapDn(ldapDn);
        sa.setLdapSourceDirectory(dir);
        sa.setActive(true);
        return sa;
    }

    private AdminAccount adminAccount(Tenant tenant, String username) {
        AdminAccount a = new AdminAccount();
        a.setId(adminId);
        a.setTenant(tenant);
        a.setUsername(username);
        a.setActive(true);
        return a;
    }

    private Tenant tenant(String slug) {
        Tenant t = new Tenant();
        t.setId(tenantId);
        t.setSlug(slug);
        t.setName(slug);
        t.setEnabled(true);
        return t;
    }

    private DirectoryConnection ldapDirectoryConnection() {
        DirectoryConnection dc = new DirectoryConnection();
        dc.setId(UUID.randomUUID());
        dc.setDisplayName("ldap-test");
        dc.setHost("localhost");
        dc.setPort(ldapServer.getListenPort());
        dc.setSslMode(SslMode.NONE);
        dc.setTrustAllCerts(false);
        dc.setPoolConnectTimeoutSeconds(5);
        dc.setPoolResponseTimeoutSeconds(10);
        return dc;
    }

    private TenantAuthConfig authConfig(AuthType type, DirectoryConnection dir, String pattern) {
        TenantAuthConfig cfg = new TenantAuthConfig();
        cfg.setAuthType(type);
        cfg.setLdapDirectory(dir);
        cfg.setLdapBindDnPattern(pattern);
        return cfg;
    }
}
