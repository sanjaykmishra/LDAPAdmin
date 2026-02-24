package com.ldapadmin.repository;

import com.ldapadmin.entity.AdminBranchRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminBranchRestrictionRepository extends JpaRepository<AdminBranchRestriction, UUID> {

    /** All branch restrictions for a given admin on a specific directory. */
    List<AdminBranchRestriction> findAllByAdminAccountIdAndDirectoryId(UUID adminAccountId, UUID directoryId);

    /** All branch restrictions for a given admin across all directories. */
    List<AdminBranchRestriction> findAllByAdminAccountId(UUID adminAccountId);

    boolean existsByAdminAccountIdAndDirectoryIdAndBranchDn(UUID adminAccountId, UUID directoryId, String branchDn);

    void deleteAllByAdminAccountIdAndDirectoryId(UUID adminAccountId, UUID directoryId);
}
