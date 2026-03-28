package com.ldapadmin.repository;

import com.ldapadmin.entity.SodViolation;
import com.ldapadmin.entity.enums.SodViolationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SodViolationRepository extends JpaRepository<SodViolation, UUID> {

    List<SodViolation> findByPolicyId(UUID policyId);

    List<SodViolation> findByStatus(SodViolationStatus status);

    long countByStatus(SodViolationStatus status);

    @Query("SELECT v FROM SodViolation v JOIN FETCH v.policy WHERE v.policy.directory.id = :directoryId AND v.status = :status")
    List<SodViolation> findByDirectoryIdAndStatus(@Param("directoryId") UUID directoryId,
                                                   @Param("status") SodViolationStatus status);

    @Query("SELECT v FROM SodViolation v JOIN FETCH v.policy WHERE v.policy.directory.id = :directoryId")
    List<SodViolation> findByDirectoryId(@Param("directoryId") UUID directoryId);

    @Query("SELECT v FROM SodViolation v WHERE v.policy.directory.id = :directoryId AND v.policy.id = :policyId")
    List<SodViolation> findByDirectoryIdAndPolicyId(@Param("directoryId") UUID directoryId,
                                                     @Param("policyId") UUID policyId);

    long countByPolicyIdAndStatus(UUID policyId, SodViolationStatus status);

    @Query("SELECT v FROM SodViolation v WHERE v.policy.id = :policyId " +
           "AND LOWER(v.userDn) = LOWER(:userDn) AND v.status = :status ORDER BY v.detectedAt DESC")
    List<SodViolation> findByPolicyIdAndUserDnIgnoreCaseAndStatus(@Param("policyId") UUID policyId,
                                                                   @Param("userDn") String userDn,
                                                                   @Param("status") SodViolationStatus status);

    @Query("SELECT COUNT(v) FROM SodViolation v WHERE v.policy.directory.id = :directoryId AND v.status = :status")
    long countByDirectoryIdAndStatus(@Param("directoryId") UUID directoryId,
                                     @Param("status") SodViolationStatus status);

    @Query("SELECT v FROM SodViolation v WHERE v.status = :status AND v.exemptionExpiresAt IS NOT NULL AND v.exemptionExpiresAt < :now")
    List<SodViolation> findExpiredExemptions(@Param("status") SodViolationStatus status,
                                             @Param("now") OffsetDateTime now);
}
