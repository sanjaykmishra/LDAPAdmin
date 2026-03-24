package com.ldapadmin.repository;

import com.ldapadmin.entity.SodViolation;
import com.ldapadmin.entity.enums.SodViolationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SodViolationRepository extends JpaRepository<SodViolation, UUID> {

    List<SodViolation> findByPolicyId(UUID policyId);

    List<SodViolation> findByStatus(SodViolationStatus status);

    long countByStatus(SodViolationStatus status);

    @Query("SELECT v FROM SodViolation v WHERE v.policy.directory.id = :directoryId AND v.status = :status")
    List<SodViolation> findByDirectoryIdAndStatus(@Param("directoryId") UUID directoryId,
                                                   @Param("status") SodViolationStatus status);

    @Query("SELECT v FROM SodViolation v WHERE v.policy.directory.id = :directoryId")
    List<SodViolation> findByDirectoryId(@Param("directoryId") UUID directoryId);

    long countByPolicyIdAndStatus(UUID policyId, SodViolationStatus status);

    Optional<SodViolation> findByPolicyIdAndUserDnAndStatus(UUID policyId, String userDn, SodViolationStatus status);
}
