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
import com.ldapadmin.exception.LdapConnectionException;
import com.ldapadmin.ldap.LdapConnectionFactory;
import com.ldapadmin.repository.AdminAccountRepository;
import com.ldapadmin.repository.SuperadminAccountRepository;
import com.ldapadmin.repository.TenantAuthConfigRepository;
import com.ldapadmin.repository.TenantRepository;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Orchestrates the login flow for both superadmins and tenant admins.
 *
 * <p>Superadmin login: {@code tenantSlug} absent → local bcrypt or LDAP bind
 * against the superadmin's source directory.</p>
 *
 * <p>Admin login: {@code tenantSlug} present → look up tenant's
 * {@link TenantAuthConfig} and authenticate according to its
 * {@link AuthType}:</p>
 * <ul>
 *   <li>{@code LDAP_BIND} — substitutes {@code {username}} in the bind DN
 *       pattern and performs a one-shot LDAP bind</li>
 *   <li>{@code SAML} — password login is not applicable; callers should
 *       initiate the SAML SSO flow instead</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationService {

    private final SuperadminAccountRepository superadminRepo;
    private final AdminAccountRepository      adminRepo;
    private final TenantRepository            tenantRepo;
    private final TenantAuthConfigRepository  authConfigRepo;
    private final LdapConnectionFactory       connectionFactory;
    private final JwtTokenService             jwtTokenService;
    private final PasswordEncoder             passwordEncoder;

    /**
     * Authenticates the login request and returns a signed JWT on success.
     *
     * @throws BadCredentialsException   on invalid credentials
     * @throws LdapConnectionException   if the LDAP server cannot be reached
     */
    @Transactional
    public LoginResponse login(LoginRequest req) {
        if (req.tenantSlug() == null || req.tenantSlug().isBlank()) {
            return loginSuperadmin(req.username(), req.password());
        }
        return loginAdmin(req.username(), req.password(), req.tenantSlug());
    }

    // ── Superadmin ────────────────────────────────────────────────────────────

    private LoginResponse loginSuperadmin(String username, String password) {
        try {
            SuperadminAccount account = superadminRepo.findByUsernameAndActiveTrue(username)
                    .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

            if (account.getAccountType() == AccountType.LOCAL) {
                if (!passwordEncoder.matches(password, account.getPasswordHash())) {
                    throw new BadCredentialsException("Bad credentials");
                }
            } else {
                // LDAP-sourced superadmin — verify via bind against source directory
                ldapBind(account.getLdapSourceDirectory(), account.getLdapDn(), password);
            }

            account.setLastLoginAt(OffsetDateTime.now());

            AuthPrincipal principal = new AuthPrincipal(
                    PrincipalType.SUPERADMIN, account.getId(), null, account.getUsername());
            return buildResponse(principal);
        } catch (BadCredentialsException e) {
            log.warn("Failed superadmin login attempt for username '{}'", username);
            throw e;
        }
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    private LoginResponse loginAdmin(String username, String password, String tenantSlug) {
        try {
            Tenant tenant = tenantRepo.findBySlugAndEnabledTrue(tenantSlug)
                    .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

            AdminAccount account = adminRepo
                    .findByTenantIdAndUsernameAndActiveTrue(tenant.getId(), username)
                    .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

            TenantAuthConfig authConfig = authConfigRepo.findByTenantId(tenant.getId())
                    .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

            if (authConfig.getAuthType() == AuthType.LDAP_BIND) {
                String bindDn = authConfig.getLdapBindDnPattern()
                        .replace("{username}", username);
                ldapBind(authConfig.getLdapDirectory(), bindDn, password);
            } else {
                // SAML tenants must use the SSO flow; password login is not supported
                throw new BadCredentialsException(
                        "Password login is not available for this tenant; please use SSO");
            }

            account.setLastLoginAt(OffsetDateTime.now());

            AuthPrincipal principal = new AuthPrincipal(
                    PrincipalType.ADMIN, account.getId(), tenant.getId(), account.getUsername());
            return buildResponse(principal);
        } catch (BadCredentialsException e) {
            log.warn("Failed admin login attempt for username '{}' in tenant '{}'", username, tenantSlug);
            throw e;
        }
    }

    // ── LDAP bind helper ──────────────────────────────────────────────────────

    /**
     * Opens a one-shot LDAP connection (not pooled), performs a simple bind,
     * and closes the connection.  Used solely to verify credentials.
     *
     * @throws BadCredentialsException on {@code INVALID_CREDENTIALS}
     * @throws LdapConnectionException on connection or protocol errors
     */
    private void ldapBind(DirectoryConnection dir, String bindDn, String password) {
        if (dir == null) {
            throw new BadCredentialsException("Bad credentials");
        }
        try (LDAPConnection conn = connectionFactory.openUnboundConnection(dir)) {
            BindResult result = conn.bind(new SimpleBindRequest(bindDn, password));
            if (result.getResultCode() != ResultCode.SUCCESS) {
                throw new BadCredentialsException("Bad credentials");
            }
        } catch (LDAPException ex) {
            if (ex.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
                throw new BadCredentialsException("Bad credentials");
            }
            throw new LdapConnectionException(
                    "LDAP bind failed for directory [" + dir.getDisplayName() + "]: "
                    + ex.getMessage(), ex);
        }
    }

    // ── Response builder ──────────────────────────────────────────────────────

    private LoginResponse buildResponse(AuthPrincipal principal) {
        String token = jwtTokenService.issue(principal);
        return new LoginResponse(
                token,
                principal.username(),
                principal.type().name(),
                principal.id().toString(),
                principal.tenantId() != null ? principal.tenantId().toString() : null);
    }
}
