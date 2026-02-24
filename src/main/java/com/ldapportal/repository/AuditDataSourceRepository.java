package com.ldapportal.repository;

import com.ldapportal.entity.AuditDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditDataSourceRepository extends JpaRepository<AuditDataSource, UUID> {

    List<AuditDataSource> findAllByTenantId(UUID tenantId);

    List<AuditDataSource> findAllByTenantIdAndEnabledTrue(UUID tenantId);

    Optional<AuditDataSource> findByIdAndTenantId(UUID id, UUID tenantId);
}
