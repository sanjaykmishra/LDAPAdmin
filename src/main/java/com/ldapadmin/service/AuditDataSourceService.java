package com.ldapadmin.service;

import com.ldapadmin.dto.audit.AuditSourceRequest;
import com.ldapadmin.dto.audit.AuditSourceResponse;
import com.ldapadmin.entity.AuditDataSource;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AuditDataSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditDataSourceService {

    private final AuditDataSourceRepository auditSourceRepo;
    private final EncryptionService         encryptionService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AuditSourceResponse> list() {
        return auditSourceRepo.findAll().stream()
                .map(AuditSourceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AuditSourceResponse get(UUID id) {
        return AuditSourceResponse.from(load(id));
    }

    @Transactional
    public AuditSourceResponse create(AuditSourceRequest req) {
        String encryptedPassword = encryptionService.encrypt(req.bindPassword());
        AuditDataSource src = new AuditDataSource();
        applyRequest(src, req, encryptedPassword);
        return AuditSourceResponse.from(auditSourceRepo.save(src));
    }

    @Transactional
    public AuditSourceResponse update(UUID id, AuditSourceRequest req) {
        AuditDataSource src = load(id);
        String encryptedPassword = (req.bindPassword() != null && !req.bindPassword().isBlank())
                ? encryptionService.encrypt(req.bindPassword())
                : src.getBindPasswordEncrypted();
        applyRequest(src, req, encryptedPassword);
        return AuditSourceResponse.from(auditSourceRepo.save(src));
    }

    @Transactional
    public void delete(UUID id) {
        auditSourceRepo.delete(load(id));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyRequest(AuditDataSource src, AuditSourceRequest req,
                              String encryptedPassword) {
        src.setDisplayName(req.displayName());
        src.setHost(req.host());
        src.setPort(req.port());
        src.setSslMode(req.sslMode());
        src.setTrustAllCerts(req.trustAllCerts());
        src.setTrustedCertificatePem(req.trustedCertificatePem());
        src.setBindDn(req.bindDn());
        src.setBindPasswordEncrypted(encryptedPassword);
        src.setChangelogBaseDn(req.changelogBaseDn() != null
                ? req.changelogBaseDn() : "cn=changelog");
        src.setBranchFilterDn(req.branchFilterDn());
        src.setEnabled(req.enabled());
    }

    private AuditDataSource load(UUID id) {
        return auditSourceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditDataSource", id));
    }
}
