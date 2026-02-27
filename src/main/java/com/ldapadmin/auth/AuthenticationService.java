package com.ldapadmin.auth;

import com.ldapadmin.auth.dto.LoginRequest;
import com.ldapadmin.auth.dto.LoginResponse;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.entity.enums.SslMode;
import com.ldapadmin.exception.LdapConnectionException;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.ApplicationSettingsRepository;
import com.ldapadmin.service.EncryptionService;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;

/**
 * Orchestrates the unified login flow for all accounts (superadmins and admins).
 *
 * <p>Authentication method is determined by {@link Account#getAuthType()}:
 * <ul>
 *   <li>{@code LOCAL} — bcrypt password check against {@code accounts.password_hash}</li>
 *   <li>{@code LDAP}  — one-shot LDAP bind using the server configured in
 *       {@link ApplicationSettings} and the bind DN pattern from
 *       {@code ldap_auth_bind_dn_pattern}</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationService {

    private final AccountRepository             accountRepo;
    private final ApplicationSettingsRepository settingsRepo;
    private final JwtTokenService               jwtTokenService;
    private final PasswordEncoder               passwordEncoder;
    private final EncryptionService             encryptionService;

    /**
     * Authenticates the login request and returns a signed JWT on success.
     *
     * @throws BadCredentialsException on invalid credentials or unknown username
     * @throws LdapConnectionException if the LDAP server cannot be reached (LDAP auth only)
     */
    @Transactional
    public LoginResponse login(LoginRequest req) {
        try {
            Account account = accountRepo.findByUsernameAndActiveTrue(req.username())
                    .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

            if (account.getAuthType() == AccountType.LOCAL) {
                if (!passwordEncoder.matches(req.password(), account.getPasswordHash())) {
                    throw new BadCredentialsException("Bad credentials");
                }
            } else {
                // LDAP auth — bind against the server in application_settings
                ApplicationSettings settings = settingsRepo.findFirst()
                        .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
                String pattern = settings.getLdapAuthBindDnPattern();
                if (pattern == null || pattern.isBlank()) {
                    throw new BadCredentialsException("Bad credentials");
                }
                String bindDn = pattern.replace("{username}", req.username());
                ldapBind(settings, bindDn, req.password());
            }

            account.setLastLoginAt(Instant.now());

            PrincipalType type = account.getRole() == AccountRole.SUPERADMIN
                    ? PrincipalType.SUPERADMIN : PrincipalType.ADMIN;
            AuthPrincipal principal = new AuthPrincipal(type, account.getId(), account.getUsername());
            return buildResponse(principal);

        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for username '{}'", req.username());
            throw e;
        }
    }

    // ── LDAP bind helper ──────────────────────────────────────────────────────

    /**
     * Opens a one-shot LDAP connection using the auth config from
     * {@link ApplicationSettings}, performs a simple bind, then closes the connection.
     *
     * @throws BadCredentialsException on {@code INVALID_CREDENTIALS}
     * @throws LdapConnectionException on connection or protocol errors
     */
    private void ldapBind(ApplicationSettings settings, String bindDn, String password) {
        String  host    = settings.getLdapAuthHost();
        int     port    = settings.getLdapAuthPort() != null ? settings.getLdapAuthPort() : 389;
        SslMode sslMode = settings.getLdapAuthSslMode() != null
                ? settings.getLdapAuthSslMode() : SslMode.NONE;

        if (host == null || host.isBlank()) {
            throw new BadCredentialsException("Bad credentials");
        }

        try (LDAPConnection conn = openRawConnection(host, port, sslMode,
                settings.isLdapAuthTrustAllCerts(), settings.getLdapAuthTrustedCertPem())) {
            BindResult result = conn.bind(new SimpleBindRequest(bindDn, password));
            if (result.getResultCode() != ResultCode.SUCCESS) {
                throw new BadCredentialsException("Bad credentials");
            }
        } catch (LDAPException ex) {
            if (ex.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
                throw new BadCredentialsException("Bad credentials");
            }
            throw new LdapConnectionException(
                    "LDAP auth bind failed for [" + host + ":" + port + "]: " + ex.getMessage(), ex);
        }
    }

    /**
     * Opens a raw, unbound LDAP connection (no pool) using the supplied parameters.
     */
    private LDAPConnection openRawConnection(String host, int port, SslMode sslMode,
                                             boolean trustAllCerts, String trustedCertPem) {
        try {
            if (sslMode == SslMode.LDAPS) {
                SSLUtil sslUtil = buildSslUtil(trustAllCerts, trustedCertPem);
                return new LDAPConnection(sslUtil.createSSLSocketFactory(), host, port);
            }

            LDAPConnection conn = new LDAPConnection(host, port);

            if (sslMode == SslMode.STARTTLS) {
                SSLUtil sslUtil = buildSslUtil(trustAllCerts, trustedCertPem);
                ExtendedResult result = conn.processExtendedOperation(
                        new StartTLSExtendedRequest(sslUtil.createSSLContext()));
                if (!result.getResultCode().equals(ResultCode.SUCCESS)) {
                    conn.close();
                    throw new LdapConnectionException(
                            "STARTTLS negotiation failed: " + result.getResultCode());
                }
            }
            return conn;

        } catch (LdapConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new LdapConnectionException(
                    "Failed to open LDAP auth connection to [" + host + ":" + port + "]: "
                    + e.getMessage(), e);
        }
    }

    private SSLUtil buildSslUtil(boolean trustAllCerts, String trustedCertPem) throws Exception {
        if (trustAllCerts) {
            return new SSLUtil(new TrustAllTrustManager());
        }
        if (trustedCertPem != null && !trustedCertPem.isBlank()) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certs = cf.generateCertificates(
                    new ByteArrayInputStream(trustedCertPem.getBytes(StandardCharsets.UTF_8)));
            KeyStore ts = KeyStore.getInstance("JKS");
            ts.load(null, null);
            int i = 0;
            for (Certificate cert : certs) ts.setCertificateEntry("ca-" + i++, cert);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
            return new SSLUtil(tmf.getTrustManagers());
        }
        return new SSLUtil();
    }

    // ── Response builder ──────────────────────────────────────────────────────

    private LoginResponse buildResponse(AuthPrincipal principal) {
        String token = jwtTokenService.issue(principal);
        return new LoginResponse(token, principal.username(), principal.type().name(),
                principal.id().toString());
    }
}
