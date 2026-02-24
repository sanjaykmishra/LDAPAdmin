package com.ldapadmin.repository;

import com.ldapadmin.entity.AdminAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminAccountRepository extends JpaRepository<AdminAccount, UUID> {

    Optional<AdminAccount> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<AdminAccount> findByTenantIdAndUsernameAndActiveTrue(UUID tenantId, String username);

    Optional<AdminAccount> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<AdminAccount> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<AdminAccount> findAllByTenantIdAndActiveTrue(UUID tenantId);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);
}
