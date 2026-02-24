package com.ldapportal.repository;

import com.ldapportal.entity.AttributeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttributeProfileRepository extends JpaRepository<AttributeProfile, UUID> {

    /** Lookup a profile by the exact (directory, branch DN) pair. */
    Optional<AttributeProfile> findByDirectoryIdAndBranchDn(UUID directoryId, String branchDn);

    /** Fetch the directory-level default profile (branchDn = "*", isDefault = true). */
    Optional<AttributeProfile> findByDirectoryIdAndIsDefaultTrue(UUID directoryId);

    List<AttributeProfile> findAllByDirectoryId(UUID directoryId);

    List<AttributeProfile> findAllByTenantId(UUID tenantId);

    boolean existsByDirectoryIdAndBranchDn(UUID directoryId, String branchDn);
}
