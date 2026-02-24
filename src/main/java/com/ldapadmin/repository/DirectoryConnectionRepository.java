package com.ldapadmin.repository;

import com.ldapadmin.entity.DirectoryConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DirectoryConnectionRepository extends JpaRepository<DirectoryConnection, UUID> {

    List<DirectoryConnection> findAllByTenantId(UUID tenantId);

    List<DirectoryConnection> findAllByTenantIdAndEnabledTrue(UUID tenantId);

    Optional<DirectoryConnection> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DirectoryConnection> findBySuperadminSourceTrue();

    List<DirectoryConnection> findAllByAuditDataSourceId(UUID auditDataSourceId);
}
