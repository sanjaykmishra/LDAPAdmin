package com.ldapadmin.repository;

import com.ldapadmin.entity.AdminBranchRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminBranchRestrictionRepository extends JpaRepository<AdminBranchRestriction, UUID> {

    /** All branch restrictions for a given admin within a specific realm. */
    List<AdminBranchRestriction> findAllByAdminAccountIdAndRealmId(UUID adminAccountId, UUID realmId);

    /** All branch restrictions for a given admin across all realms in a directory. */
    List<AdminBranchRestriction> findAllByAdminAccountIdAndRealmDirectoryId(UUID adminAccountId, UUID directoryId);

    /** All branch restrictions for a given admin across all realms. */
    List<AdminBranchRestriction> findAllByAdminAccountId(UUID adminAccountId);

    boolean existsByAdminAccountIdAndRealmIdAndBranchDn(UUID adminAccountId, UUID realmId, String branchDn);

    void deleteAllByAdminAccountIdAndRealmId(UUID adminAccountId, UUID realmId);
}
