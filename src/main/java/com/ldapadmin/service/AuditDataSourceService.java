package com.ldapadmin.service;

import com.ldapadmin.dto.audit.AuditSourceRequest;
import com.ldapadmin.dto.audit.AuditSourceResponse;
import com.ldapadmin.entity.AuditDataSource;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.AuditSource;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AuditDataSourceRepository;
import com.ldapadmin.repository.AuditEventRepository;
import com.ldapadmin.repository.TenantRepository;
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
    private final TenantRepository          tenantRepo;
    private final EncryptionService         encryptionService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AuditSourceResponse> list(UUID tenantId) {
        requireTenant(tenantId);
        return auditSourceRepo.findAllByTenantId(tenantId)
                .stream().map(AuditSourceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AuditSourceResponse get(UUID tenantId, UUID id) {
        return AuditSourceResponse.from(load(tenantId, id));
    }

    @Transactional
    public AuditSourceResponse create(UUID tenantId, AuditSourceRequest req) {
        Tenant tenant = requireTenant(tenantId);

        String encryptedPassword = encryptionService.encrypt(req.bindPassword());

        AuditDataSource src = new AuditDataSource();
        src.setTenant(tenant);
        applyRequest(src, req, encryptedPassword);

        return AuditSourceResponse.from(auditSourceRepo.save(src));
    }

    @Transactional
    public AuditSourceResponse update(UUID tenantId, UUID id, AuditSourceRequest req) {
        AuditDataSource src = load(tenantId, id);

        String encryptedPassword = (req.bindPassword() != null && !req.bindPassword().isBlank())
                ? encryptionService.encrypt(req.bindPassword())
                : src.getBindPasswordEncrypted();

        applyRequest(src, req, encryptedPassword);
        return AuditSourceResponse.from(auditSourceRepo.save(src));
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        AuditDataSource src = load(tenantId, id);
        auditSourceRepo.delete(src);
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

    private AuditDataSource load(UUID tenantId, UUID id) {
        return auditSourceRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditDataSource", id));
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }
}
