package com.ldapadmin.service;

import com.ldapadmin.dto.directory.DirectoryConnectionRequest;
import com.ldapadmin.dto.directory.DirectoryConnectionResponse;
import com.ldapadmin.dto.directory.TestConnectionRequest;
import com.ldapadmin.dto.directory.TestConnectionResult;
import com.ldapadmin.entity.AuditDataSource;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.DirectoryGroupBaseDn;
import com.ldapadmin.entity.DirectoryUserBaseDn;
import com.ldapadmin.entity.enums.SslMode;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapConnectionFactory;
import com.ldapadmin.repository.AuditDataSourceRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.DirectoryGroupBaseDnRepository;
import com.ldapadmin.repository.DirectoryUserBaseDnRepository;
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

    private final DirectoryConnectionRepository  dirRepo;
    private final DirectoryUserBaseDnRepository  userBaseDnRepo;
    private final DirectoryGroupBaseDnRepository groupBaseDnRepo;
    private final AuditDataSourceRepository      auditSourceRepo;
    private final EncryptionService              encryptionService;
    private final LdapConnectionFactory          connectionFactory;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<DirectoryConnectionResponse> listDirectories() {
        return dirRepo.findAll().stream().map(this::toResponse).toList();
    }

    public DirectoryConnectionResponse getDirectory(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public DirectoryConnectionResponse createDirectory(DirectoryConnectionRequest req) {
        DirectoryConnection dc = new DirectoryConnection();
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
    public DirectoryConnectionResponse updateDirectory(UUID id, DirectoryConnectionRequest req) {
        DirectoryConnection dc = require(id);
        applyRequest(dc, req);

        if (req.bindPassword() != null && !req.bindPassword().isBlank()) {
            dc.setBindPasswordEncrypted(encryptionService.encrypt(req.bindPassword()));
        }

        connectionFactory.evict(dc.getId());
        dc = dirRepo.save(dc);
        saveBaseDns(dc, req);
        return toResponse(dc);
    }

    @Transactional
    public void deleteDirectory(UUID id) {
        DirectoryConnection dc = require(id);
        connectionFactory.evict(dc.getId());
        dirRepo.delete(dc);
    }

    public void evictPool(UUID id) {
        require(id);
        connectionFactory.evict(id);
        log.info("Pool evicted for directory {}", id);
    }

    // ── Test connection ───────────────────────────────────────────────────────

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
        dc.setEnabled(req.enabled());

        if (req.auditDataSourceId() != null) {
            AuditDataSource auditSrc = auditSourceRepo.findById(req.auditDataSourceId())
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

    private DirectoryConnectionResponse toResponse(DirectoryConnection dc) {
        List<DirectoryUserBaseDn>  users  = userBaseDnRepo.findAllByDirectoryIdOrderByDisplayOrderAsc(dc.getId());
        List<DirectoryGroupBaseDn> groups = groupBaseDnRepo.findAllByDirectoryIdOrderByDisplayOrderAsc(dc.getId());
        return DirectoryConnectionResponse.from(dc, users, groups);
    }

    private DirectoryConnection require(UUID id) {
        return dirRepo.findById(id)
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
