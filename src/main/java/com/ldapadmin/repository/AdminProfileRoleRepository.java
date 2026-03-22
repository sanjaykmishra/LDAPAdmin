package com.ldapadmin.repository;

import com.ldapadmin.entity.AdminProfileRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminProfileRoleRepository extends JpaRepository<AdminProfileRole, UUID> {

    Optional<AdminProfileRole> findByAdminAccountIdAndProfileId(UUID adminAccountId, UUID profileId);

    List<AdminProfileRole> findAllByAdminAccountId(UUID adminAccountId);

    List<AdminProfileRole> findAllByProfileId(UUID profileId);

    boolean existsByAdminAccountIdAndProfileId(UUID adminAccountId, UUID profileId);

    boolean existsByAdminAccountIdAndProfileDirectoryId(UUID adminAccountId, UUID directoryId);

    void deleteByAdminAccountIdAndProfileId(UUID adminAccountId, UUID profileId);
}
