package com.ldapadmin.repository;

import com.ldapadmin.entity.TenantAuthConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantAuthConfigRepository extends JpaRepository<TenantAuthConfig, UUID> {

    Optional<TenantAuthConfig> findByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);
}
