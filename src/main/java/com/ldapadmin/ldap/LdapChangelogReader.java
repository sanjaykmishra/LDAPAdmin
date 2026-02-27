package com.ldapadmin.ldap;

import com.ldapadmin.entity.AuditDataSource;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.SslMode;
import com.ldapadmin.exception.LdapConnectionException;
import com.ldapadmin.repository.AuditDataSourceRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.service.AuditService;
import com.ldapadmin.service.EncryptionService;
import com.unboundid.ldap.sdk.*;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Scheduled poller that reads LDAP {@code cn=changelog} entries from each
 * enabled {@link AuditDataSource} and persists them as
 * {@link com.ldapadmin.entity.AuditEvent} records.
 *
 * <p>The poller runs every 60 seconds by default (configurable via
 * {@code app.audit.changelog-poll-interval-ms}).  It uses an independent
 * short-lived LDAP connection per poll (not a pool) so that disabled or
 * misconfigured sources don't block the scheduler thread.</p>
 *
 * <p>Idempotency: each changelog entry is keyed by
 * {@code (directoryId, changeNumber)}.  The {@link AuditService} guard
 * prevents duplicate inserts even if the poller restarts mid-run.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LdapChangelogReader {

    private static final int MAX_CHANGELOG_ENTRIES_PER_POLL = 500;

    /** GeneralizedTime format used in LDAP changeLog timestamps. */
    private static final DateTimeFormatter GENERALIZED_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss[.SSS][.SS][.S]'Z'");

    private final AuditDataSourceRepository   auditSourceRepo;
    private final DirectoryConnectionRepository dirRepo;
    private final AuditService                 auditService;
    private final EncryptionService            encryptionService;

    // ── Scheduler ─────────────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${app.audit.changelog-poll-interval-ms:60000}",
               initialDelayString = "${app.audit.changelog-poll-initial-delay-ms:15000}")
    public void pollAll() {
        List<AuditDataSource> sources = auditSourceRepo.findAll().stream()
                .filter(AuditDataSource::isEnabled)
                .toList();

        for (AuditDataSource src : sources) {
            try {
                pollSource(src);
            } catch (Exception ex) {
                log.warn("Changelog poll failed for source [{}]: {}",
                        src.getDisplayName(), ex.getMessage(), ex);
            }
        }
    }

    // ── Per-source poll ───────────────────────────────────────────────────────

    private void pollSource(AuditDataSource src) {
        log.debug("Polling changelog for audit source [{}]", src.getDisplayName());

        // Resolve optional directory association for denormalised fields
        List<DirectoryConnection> linkedDirs = dirRepo.findAll().stream()
                .filter(dc -> dc.getAuditDataSource() != null
                        && dc.getAuditDataSource().getId().equals(src.getId()))
                .toList();

        try (LDAPConnection conn = openConnection(src)) {
            String filter = "(objectClass=changeLogEntry)";
            if (src.getBranchFilterDn() != null && !src.getBranchFilterDn().isBlank()) {
                filter = "(&(objectClass=changeLogEntry)(targetDN=" + src.getBranchFilterDn() + "*))";
            }

            SearchRequest searchReq = new SearchRequest(
                    src.getChangelogBaseDn(),
                    SearchScope.ONE,
                    filter,
                    "changeNumber", "changeType", "targetDN",
                    "changes", "newRDN", "deleteOldRDN", "newSuperior",
                    "changeTime", "creatorsName");
            searchReq.setSizeLimit(MAX_CHANGELOG_ENTRIES_PER_POLL);

            SearchResult result = conn.search(searchReq);

            for (SearchResultEntry entry : result.getSearchEntries()) {
                processEntry(src, linkedDirs, entry);
            }

            log.debug("Processed {} changelog entries for source [{}]",
                    result.getEntryCount(), src.getDisplayName());

        } catch (LDAPException ex) {
            throw new LdapConnectionException(
                    "Changelog read failed for [" + src.getDisplayName() + "]: " + ex.getMessage(), ex);
        }
    }

    private void processEntry(AuditDataSource src,
                              List<DirectoryConnection> linkedDirs,
                              SearchResultEntry entry) {
        String changeNumber = entry.getAttributeValue("changeNumber");
        if (changeNumber == null) {
            return;
        }

        String targetDn    = entry.getAttributeValue("targetDN");
        String changeType  = entry.getAttributeValue("changeType");
        String changeTime  = entry.getAttributeValue("changeTime");

        // Resolve directory association by matching the targetDN to linked dirs
        DirectoryConnection matchedDir = linkedDirs.stream()
                .filter(dc -> targetDn != null
                        && targetDn.toLowerCase().endsWith(dc.getBaseDn().toLowerCase()))
                .findFirst().orElse(null);

        UUID   directoryId   = matchedDir != null ? matchedDir.getId() : null;
        String directoryName = matchedDir != null ? matchedDir.getDisplayName() : null;

        // Skip if already recorded
        if (directoryId != null &&
                auditService.isChangelogEventRecorded(directoryId, changeNumber)) {
            return;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("changeType",  changeType);
        detail.put("changes",     entry.getAttributeValue("changes"));
        detail.put("creatorsName", entry.getAttributeValue("creatorsName"));
        if (entry.getAttributeValue("newRDN") != null) {
            detail.put("newRDN",         entry.getAttributeValue("newRDN"));
            detail.put("deleteOldRDN",   entry.getAttributeValue("deleteOldRDN"));
            detail.put("newSuperior",    entry.getAttributeValue("newSuperior"));
        }

        OffsetDateTime occurredAt = parseGeneralizedTime(changeTime);

        auditService.recordChangelogEvent(
                directoryId,
                directoryName,
                targetDn,
                changeNumber,
                detail,
                occurredAt);
    }

    // ── LDAP connection helpers ───────────────────────────────────────────────

    private LDAPConnection openConnection(AuditDataSource src) {
        try {
            String password = encryptionService.decrypt(src.getBindPasswordEncrypted());
            LDAPConnectionOptions opts = new LDAPConnectionOptions();
            opts.setConnectTimeoutMillis(10_000);
            opts.setResponseTimeoutMillis(30_000L);

            LDAPConnection conn;
            if (src.getSslMode() == SslMode.LDAPS) {
                SSLUtil sslUtil = buildSslUtil(src);
                conn = new LDAPConnection(sslUtil.createSSLSocketFactory(),
                        opts, src.getHost(), src.getPort());
            } else {
                conn = new LDAPConnection(opts, src.getHost(), src.getPort());
                if (src.getSslMode() == SslMode.STARTTLS) {
                    SSLUtil sslUtil = buildSslUtil(src);
                    conn.processExtendedOperation(
                            new com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest(
                                    sslUtil.createSSLContext()));
                }
            }

            conn.bind(src.getBindDn(), password);
            return conn;

        } catch (LdapConnectionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LdapConnectionException(
                    "Failed to connect to audit source [" + src.getDisplayName() + "]: "
                    + ex.getMessage(), ex);
        }
    }

    private SSLUtil buildSslUtil(AuditDataSource src) throws Exception {
        if (src.isTrustAllCerts()) {
            return new SSLUtil(new TrustAllTrustManager());
        }
        if (src.getTrustedCertificatePem() != null && !src.getTrustedCertificatePem().isBlank()) {
            return new SSLUtil(buildPemTrustManagers(src.getTrustedCertificatePem()));
        }
        return new SSLUtil();
    }

    private TrustManager[] buildPemTrustManagers(String pem) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("ldap-audit-ca", cert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        return tmf.getTrustManagers();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private OffsetDateTime parseGeneralizedTime(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(value, GENERALIZED_TIME);
        } catch (DateTimeParseException ex) {
            log.debug("Cannot parse changelog timestamp '{}', using now", value);
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
