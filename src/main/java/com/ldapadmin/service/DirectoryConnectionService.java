package com.ldapadmin.service;

import com.ldapadmin.dto.directory.DirectoryConnectionRequest;
import com.ldapadmin.dto.directory.DirectoryConnectionResponse;
import com.ldapadmin.dto.directory.TestConnectionRequest;
import com.ldapadmin.dto.directory.TestConnectionResult;
import com.ldapadmin.entity.AuditDataSource;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.DirectoryGroupBaseDn;
import com.ldapadmin.entity.DirectoryUserBaseDn;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.entity.enums.SslMode;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapConnectionFactory;
import com.ldapadmin.repository.AuditDataSourceRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.DirectoryGroupBaseDnRepository;
import com.ldapadmin.repository.DirectoryUserBaseDnRepository;
import com.ldapadmin.repository.TenantRepository;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DirectoryConnectionService {

    private final DirectoryConnectionRepository dirRepo;
    private final DirectoryUserBaseDnRepository userBaseDnRepo;
    private final DirectoryGroupBaseDnRepository groupBaseDnRepo;
    private final TenantRepository tenantRepo;
    private final AuditDataSourceRepository auditSourceRepo;
    private final EncryptionService encryptionService;
    private final LdapConnectionFactory connectionFactory;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<DirectoryConnectionResponse> listDirectories(UUID tenantId) {
        requireTenant(tenantId);
        return dirRepo.findAllByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public DirectoryConnectionResponse getDirectory(UUID tenantId, UUID id) {
        return toResponse(requireDirectory(tenantId, id));
    }

    @Transactional
    public DirectoryConnectionResponse createDirectory(UUID tenantId,
                                                       DirectoryConnectionRequest req) {
        Tenant tenant = requireTenant(tenantId);

        if (req.superadminSource()) {
            clearExistingSuperadminSource(tenantId);
        }

        DirectoryConnection dc = new DirectoryConnection();
        dc.setTenant(tenant);
        applyRequest(dc, req);

        if (req.bindPassword() != null && !req.bindPassword().isBlank()) {
            dc.setBindPasswordEncrypted(encryptionService.encrypt(req.bindPassword()));
        } else {
            throw new IllegalArgumentException("bindPassword is required when creating a directory");
        }

        dc = dirRepo.save(dc);
        saveBaseDns(dc, req);
        return toResponse(dc);
    }

    @Transactional
    public DirectoryConnectionResponse updateDirectory(UUID tenantId, UUID id,
                                                       DirectoryConnectionRequest req) {
        DirectoryConnection dc = requireDirectory(tenantId, id);

        if (req.superadminSource() && !dc.isSuperadminSource()) {
            clearExistingSuperadminSource(tenantId);
        }

        applyRequest(dc, req);

        // Only re-encrypt password if a new one was provided
        if (req.bindPassword() != null && !req.bindPassword().isBlank()) {
            dc.setBindPasswordEncrypted(encryptionService.encrypt(req.bindPassword()));
        }

        connectionFactory.evict(dc.getId());
        dc = dirRepo.save(dc);
        saveBaseDns(dc, req);
        return toResponse(dc);
    }

    @Transactional
    public void deleteDirectory(UUID tenantId, UUID id) {
        DirectoryConnection dc = requireDirectory(tenantId, id);
        connectionFactory.evict(dc.getId());
        dirRepo.delete(dc);
    }

    public void evictPool(UUID tenantId, UUID id) {
        requireDirectory(tenantId, id);
        connectionFactory.evict(id);
        log.info("Pool evicted for directory {}", id);
    }

    // ── Test connection ───────────────────────────────────────────────────────

    /**
     * Tests connectivity and bind without persisting anything.
     */
    public TestConnectionResult testConnection(TestConnectionRequest req) {
        Instant start = Instant.now();
        try {
            try (LDAPConnection conn = openTestConnection(req)) {
                BindResult result = conn.bind(
                        new SimpleBindRequest(req.bindDn(), req.bindPassword()));
                long ms = Duration.between(start, Instant.now()).toMillis();

                if (result.getResultCode() == ResultCode.SUCCESS) {
                    return new TestConnectionResult(true, "Connection and bind successful", ms);
                }
                return new TestConnectionResult(false,
                        "Bind failed: " + result.getResultCode().getName(), ms);
            }
        } catch (Exception ex) {
            long ms = Duration.between(start, Instant.now()).toMillis();
            return new TestConnectionResult(false, ex.getMessage(), ms);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void applyRequest(DirectoryConnection dc, DirectoryConnectionRequest req) {
        dc.setDisplayName(req.displayName());
        dc.setHost(req.host());
        dc.setPort(req.port());
        dc.setSslMode(req.sslMode());
        dc.setTrustAllCerts(req.trustAllCerts());
        dc.setTrustedCertificatePem(req.trustedCertificatePem());
        dc.setBindDn(req.bindDn());
        dc.setBaseDn(req.baseDn());
        dc.setObjectClasses(req.objectClasses());
        dc.setPagingSize(req.pagingSize());
        dc.setPoolMinSize(req.poolMinSize());
        dc.setPoolMaxSize(req.poolMaxSize());
        dc.setPoolConnectTimeoutSeconds(req.poolConnectTimeoutSeconds());
        dc.setPoolResponseTimeoutSeconds(req.poolResponseTimeoutSeconds());
        dc.setEnableDisableAttribute(req.enableDisableAttribute());
        dc.setEnableDisableValueType(req.enableDisableValueType());
        dc.setEnableValue(req.enableValue());
        dc.setDisableValue(req.disableValue());
        dc.setSuperadminSource(req.superadminSource());
        dc.setEnabled(req.enabled());

        if (req.auditDataSourceId() != null) {
            AuditDataSource auditSrc = auditSourceRepo
                    .findByIdAndTenantId(req.auditDataSourceId(), dc.getTenant().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "AuditDataSource", req.auditDataSourceId()));
            dc.setAuditDataSource(auditSrc);
        } else {
            dc.setAuditDataSource(null);
        }
    }

    private void saveBaseDns(DirectoryConnection dc, DirectoryConnectionRequest req) {
        userBaseDnRepo.deleteAllByDirectoryId(dc.getId());
        groupBaseDnRepo.deleteAllByDirectoryId(dc.getId());

        if (req.userBaseDns() != null) {
            req.userBaseDns().forEach(b -> {
                DirectoryUserBaseDn e = new DirectoryUserBaseDn();
                e.setDirectory(dc);
                e.setDn(b.dn());
                e.setDisplayOrder(b.displayOrder());
                userBaseDnRepo.save(e);
            });
        }
        if (req.groupBaseDns() != null) {
            req.groupBaseDns().forEach(b -> {
                DirectoryGroupBaseDn e = new DirectoryGroupBaseDn();
                e.setDirectory(dc);
                e.setDn(b.dn());
                e.setDisplayOrder(b.displayOrder());
                groupBaseDnRepo.save(e);
            });
        }
    }

    /** Clears the superadmin-source flag from any other directory in the installation. */
    private void clearExistingSuperadminSource(UUID currentTenantId) {
        dirRepo.findBySuperadminSourceTrue().ifPresent(existing -> {
            existing.setSuperadminSource(false);
            dirRepo.save(existing);
            connectionFactory.evict(existing.getId());
        });
    }

    private DirectoryConnectionResponse toResponse(DirectoryConnection dc) {
        List<DirectoryUserBaseDn>  users  = userBaseDnRepo.findAllByDirectoryIdOrderByDisplayOrderAsc(dc.getId());
        List<DirectoryGroupBaseDn> groups = groupBaseDnRepo.findAllByDirectoryIdOrderByDisplayOrderAsc(dc.getId());
        return DirectoryConnectionResponse.from(dc, users, groups);
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }

    private DirectoryConnection requireDirectory(UUID tenantId, UUID id) {
        return dirRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", id));
    }

    // ── One-shot SSL connection for test ──────────────────────────────────────

    private LDAPConnection openTestConnection(TestConnectionRequest req) throws Exception {
        LDAPConnectionOptions options = new LDAPConnectionOptions();
        options.setConnectTimeoutMillis(10_000);
        options.setResponseTimeoutMillis(10_000L);

        if (req.sslMode() == SslMode.LDAPS) {
            SSLUtil sslUtil = buildTestSslUtil(req);
            SSLSocketFactory sf = sslUtil.createSSLSocketFactory();
            return new LDAPConnection(sf, options, req.host(), req.port());
        }

        LDAPConnection conn = new LDAPConnection(options, req.host(), req.port());

        if (req.sslMode() == SslMode.STARTTLS) {
            SSLUtil sslUtil = buildTestSslUtil(req);
            com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest startTls =
                    new com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest(
                            sslUtil.createSSLContext());
            conn.processExtendedOperation(startTls);
        }

        return conn;
    }

    private SSLUtil buildTestSslUtil(TestConnectionRequest req) throws Exception {
        if (req.trustAllCerts()) {
            return new SSLUtil(new TrustAllTrustManager());
        }
        if (req.trustedCertificatePem() != null && !req.trustedCertificatePem().isBlank()) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(
                            req.trustedCertificatePem().getBytes(StandardCharsets.UTF_8)));
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(null, null);
            ks.setCertificateEntry("trusted-ca", cert);
            javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            return new SSLUtil(tmf.getTrustManagers());
        }
        return new SSLUtil();
    }
}
