package com.ldapadmin.ldap;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.SslMode;
import com.ldapadmin.exception.LdapConnectionException;
import com.ldapadmin.service.EncryptionService;
import com.unboundid.ldap.sdk.*;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Creates and caches {@link LDAPConnectionPool} instances keyed by
 * {@link DirectoryConnection} ID.
 *
 * <p>Pools are lazily created on first use and reused for subsequent
 * operations.  When a directory connection is updated (e.g. new password,
 * changed host), the caller must call {@link #evict(UUID)} to close the
 * stale pool before the next operation recreates it with the new settings.</p>
 *
 * <p>SSL/TLS is configured per the {@link SslMode} on the connection:
 * <ul>
 *   <li>{@code NONE} — plain TCP</li>
 *   <li>{@code LDAPS} — SSL/TLS on connect (typically port 636)</li>
 *   <li>{@code STARTTLS} — plain TCP upgraded with the STARTTLS extended op</li>
 * </ul>
 * If {@code trustedCertificatePem} is set it is used as the sole trust anchor;
 * if {@code trustAllCerts} is set all server certificates are accepted;
 * otherwise the JVM default trust store is used.
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LdapConnectionFactory {

    private final EncryptionService encryptionService;

    private final ConcurrentMap<UUID, LDAPConnectionPool> pools = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the connection pool for the given {@code directoryConnection},
     * creating it if it does not already exist.
     */
    public LDAPConnectionPool getPool(DirectoryConnection directoryConnection) {
        return pools.computeIfAbsent(directoryConnection.getId(),
            id -> createPool(directoryConnection));
    }

    /**
     * Borrows a connection from the pool, executes the operation, then
     * returns the connection.  On {@link LDAPException} the connection is
     * replaced if it is no longer valid.
     */
    public <T> T withConnection(DirectoryConnection dc,
                                LdapOperation<T> operation) {
        LDAPConnectionPool pool = getPool(dc);
        LDAPConnection conn = null;
        try {
            conn = pool.getConnection();
            return operation.execute(conn);
        } catch (LDAPException e) {
            if (conn != null) {
                pool.releaseDefunctConnection(conn);
                conn = null;
            }
            throw new LdapConnectionException(
                "LDAP operation failed on [" + dc.getDisplayName() + "]: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                pool.releaseConnection(conn);
            }
        }
    }

    /**
     * Closes and removes the cached pool for the given connection ID.
     * Should be called whenever a {@link DirectoryConnection} is updated.
     */
    public void evict(UUID connectionId) {
        LDAPConnectionPool pool = pools.remove(connectionId);
        if (pool != null) {
            pool.close();
            log.info("Evicted LDAP pool for connection {}", connectionId);
        }
    }

    /**
     * Closes all pools.  Called on application shutdown.
     */
    public void closeAll() {
        pools.forEach((id, pool) -> {
            try {
                pool.close();
            } catch (Exception e) {
                log.warn("Error closing LDAP pool {}: {}", id, e.getMessage());
            }
        });
        pools.clear();
    }

    // ── Pool creation ─────────────────────────────────────────────────────────

    private LDAPConnectionPool createPool(DirectoryConnection dc) {
        try {
            String password = encryptionService.decrypt(dc.getBindPasswordEncrypted());
            SimpleBindRequest bindRequest = new SimpleBindRequest(dc.getBindDn(), password);

            LDAPConnectionOptions options = buildOptions(dc);
            ServerSet serverSet = buildServerSet(dc, options);

            LDAPConnectionPool pool;
            if (dc.getSslMode() == SslMode.STARTTLS) {
                SSLUtil sslUtil = buildSslUtil(dc);
                StartTLSPostConnectProcessor startTLS =
                    new StartTLSPostConnectProcessor(sslUtil.createSSLContext());
                pool = new LDAPConnectionPool(
                    serverSet, bindRequest,
                    dc.getPoolMinSize(), dc.getPoolMaxSize(),
                    startTLS);
            } else {
                pool = new LDAPConnectionPool(
                    serverSet, bindRequest,
                    dc.getPoolMinSize(), dc.getPoolMaxSize());
            }

            log.info("Created LDAP pool for [{}] host={}:{} ssl={} min={} max={}",
                dc.getDisplayName(), dc.getHost(), dc.getPort(),
                dc.getSslMode(), dc.getPoolMinSize(), dc.getPoolMaxSize());
            return pool;

        } catch (LdapConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new LdapConnectionException(
                "Failed to create LDAP pool for [" + dc.getDisplayName() + "]: " + e.getMessage(), e);
        }
    }

    private LDAPConnectionOptions buildOptions(DirectoryConnection dc) {
        LDAPConnectionOptions options = new LDAPConnectionOptions();
        options.setConnectTimeoutMillis(dc.getPoolConnectTimeoutSeconds() * 1_000);
        options.setResponseTimeoutMillis((long) dc.getPoolResponseTimeoutSeconds() * 1_000L);
        return options;
    }

    private ServerSet buildServerSet(DirectoryConnection dc,
                                     LDAPConnectionOptions options) throws Exception {
        if (dc.getSslMode() == SslMode.LDAPS) {
            SSLUtil sslUtil = buildSslUtil(dc);
            SSLSocketFactory sslSocketFactory = sslUtil.createSSLSocketFactory();
            return new SingleServerSet(dc.getHost(), dc.getPort(), sslSocketFactory, options);
        }
        return new SingleServerSet(dc.getHost(), dc.getPort(), options);
    }

    private SSLUtil buildSslUtil(DirectoryConnection dc) throws Exception {
        if (dc.isTrustAllCerts()) {
            return new SSLUtil(new TrustAllTrustManager());
        }
        if (dc.getTrustedCertificatePem() != null && !dc.getTrustedCertificatePem().isBlank()) {
            TrustManager[] trustManagers = buildPemTrustManagers(dc.getTrustedCertificatePem());
            return new SSLUtil(trustManagers);
        }
        // Use JVM default trust store
        return new SSLUtil();
    }

    private TrustManager[] buildPemTrustManagers(String pem) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
            new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("trusted-ca", cert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }

    // ── Functional interface ──────────────────────────────────────────────────

    @FunctionalInterface
    public interface LdapOperation<T> {
        T execute(LDAPConnection connection) throws LDAPException;
    }
}
