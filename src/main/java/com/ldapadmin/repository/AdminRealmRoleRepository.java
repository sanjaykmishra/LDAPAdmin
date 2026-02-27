package com.ldapadmin.repository;

import com.ldapadmin.entity.AdminRealmRole;
import com.ldapadmin.entity.enums.BaseRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRealmRoleRepository extends JpaRepository<AdminRealmRole, UUID> {

    Optional<AdminRealmRole> findByAdminAccountIdAndRealmId(UUID adminAccountId, UUID realmId);

    List<AdminRealmRole> findAllByAdminAccountId(UUID adminAccountId);

    List<AdminRealmRole> findAllByRealmId(UUID realmId);

    List<AdminRealmRole> findAllByRealmIdAndBaseRole(UUID realmId, BaseRole baseRole);

    boolean existsByAdminAccountIdAndRealmId(UUID adminAccountId, UUID realmId);

    /** Returns true if the admin has a role in any realm belonging to {@code directoryId}. */
    boolean existsByAdminAccountIdAndRealmDirectoryId(UUID adminAccountId, UUID directoryId);

    void deleteByAdminAccountIdAndRealmId(UUID adminAccountId, UUID realmId);
}
