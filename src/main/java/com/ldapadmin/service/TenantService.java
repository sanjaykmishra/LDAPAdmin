package com.ldapadmin.service;

import com.ldapadmin.dto.tenant.TenantAuthConfigRequest;
import com.ldapadmin.dto.tenant.TenantAuthConfigResponse;
import com.ldapadmin.dto.tenant.TenantRequest;
import com.ldapadmin.dto.tenant.TenantResponse;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.entity.TenantAuthConfig;
import com.ldapadmin.entity.enums.AuthType;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.TenantAuthConfigRepository;
import com.ldapadmin.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository            tenantRepo;
    private final TenantAuthConfigRepository  authConfigRepo;
    private final DirectoryConnectionRepository dirRepo;

    // ── Tenant CRUD ───────────────────────────────────────────────────────────

    public List<TenantResponse> listTenants() {
        return tenantRepo.findAll().stream()
                .map(TenantResponse::from)
                .toList();
    }

    public TenantResponse getTenant(UUID id) {
        return TenantResponse.from(requireTenant(id));
    }

    @Transactional
    public TenantResponse createTenant(TenantRequest req) {
        if (tenantRepo.existsBySlug(req.slug())) {
            throw new ConflictException("Tenant with slug [" + req.slug() + "] already exists");
        }
        Tenant t = new Tenant();
        t.setName(req.name());
        t.setSlug(req.slug());
        t.setEnabled(req.enabled());
        return TenantResponse.from(tenantRepo.save(t));
    }

    @Transactional
    public TenantResponse updateTenant(UUID id, TenantRequest req) {
        Tenant t = requireTenant(id);
        // Only reject slug conflicts with a *different* tenant
        if (!t.getSlug().equals(req.slug()) && tenantRepo.existsBySlug(req.slug())) {
            throw new ConflictException("Tenant with slug [" + req.slug() + "] already exists");
        }
        t.setName(req.name());
        t.setSlug(req.slug());
        t.setEnabled(req.enabled());
        return TenantResponse.from(tenantRepo.save(t));
    }

    @Transactional
    public void deleteTenant(UUID id) {
        Tenant t = requireTenant(id);
        tenantRepo.delete(t);
    }

    // ── Auth config ───────────────────────────────────────────────────────────

    public TenantAuthConfigResponse getAuthConfig(UUID tenantId) {
        requireTenant(tenantId);
        TenantAuthConfig cfg = authConfigRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Auth config not found for tenant " + tenantId));
        return TenantAuthConfigResponse.from(cfg);
    }

    @Transactional
    public TenantAuthConfigResponse saveAuthConfig(UUID tenantId, TenantAuthConfigRequest req) {
        Tenant tenant = requireTenant(tenantId);
        TenantAuthConfig cfg = authConfigRepo.findByTenantId(tenantId)
                .orElseGet(TenantAuthConfig::new);

        cfg.setTenant(tenant);
        cfg.setAuthType(req.authType());

        // Clear all auth-type-specific fields first, then populate the chosen one
        clearAuthFields(cfg);

        if (req.authType() == AuthType.LDAP_BIND) {
            if (req.ldapDirectoryId() != null) {
                DirectoryConnection dir = dirRepo.findByIdAndTenantId(
                                req.ldapDirectoryId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "DirectoryConnection", req.ldapDirectoryId()));
                cfg.setLdapDirectory(dir);
            }
            cfg.setLdapBindDnPattern(req.ldapBindDnPattern());
        } else {
            cfg.setSamlIdpType(req.samlIdpType());
            cfg.setSamlIdpMetadataUrl(req.samlIdpMetadataUrl());
            cfg.setSamlIdpMetadataXml(req.samlIdpMetadataXml());
            cfg.setSamlSpEntityId(req.samlSpEntityId());
            cfg.setSamlSpAcsUrl(req.samlSpAcsUrl());
            cfg.setSamlAttributeUsername(req.samlAttributeUsername());
            cfg.setSamlAttributeEmail(req.samlAttributeEmail());
            cfg.setSamlAttributeDisplayName(req.samlAttributeDisplayName());
            cfg.setSamlExtraAttributeMappings(req.samlExtraAttributeMappings());
        }

        return TenantAuthConfigResponse.from(authConfigRepo.save(cfg));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Tenant requireTenant(UUID id) {
        return tenantRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));
    }

    private void clearAuthFields(TenantAuthConfig cfg) {
        cfg.setLdapDirectory(null);
        cfg.setLdapBindDnPattern(null);
        cfg.setSamlIdpType(null);
        cfg.setSamlIdpMetadataUrl(null);
        cfg.setSamlIdpMetadataXml(null);
        cfg.setSamlSpEntityId(null);
        cfg.setSamlSpAcsUrl(null);
        cfg.setSamlAttributeUsername(null);
        cfg.setSamlAttributeEmail(null);
        cfg.setSamlAttributeDisplayName(null);
        cfg.setSamlExtraAttributeMappings(null);
    }
}
