package com.ldapportal.repository;

import com.ldapportal.entity.AdminDirectoryRole;
import com.ldapportal.entity.enums.BaseRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminDirectoryRoleRepository extends JpaRepository<AdminDirectoryRole, UUID> {

    /** Returns the role assignment for a specific (admin, directory) pair. */
    Optional<AdminDirectoryRole> findByAdminAccountIdAndDirectoryId(UUID adminAccountId, UUID directoryId);

    /** All directory role assignments for a given admin. */
    List<AdminDirectoryRole> findAllByAdminAccountId(UUID adminAccountId);

    /** All admins that have a role on a given directory. */
    List<AdminDirectoryRole> findAllByDirectoryId(UUID directoryId);

    /** All admins with a specific role on a given directory. */
    List<AdminDirectoryRole> findAllByDirectoryIdAndBaseRole(UUID directoryId, BaseRole baseRole);

    boolean existsByAdminAccountIdAndDirectoryId(UUID adminAccountId, UUID directoryId);

    void deleteByAdminAccountIdAndDirectoryId(UUID adminAccountId, UUID directoryId);
}
