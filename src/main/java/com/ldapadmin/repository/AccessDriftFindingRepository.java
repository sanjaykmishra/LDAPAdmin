package com.ldapadmin.repository;

import com.ldapadmin.entity.AccessDriftFinding;
import com.ldapadmin.entity.enums.DriftFindingSeverity;
import com.ldapadmin.entity.enums.DriftFindingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccessDriftFindingRepository extends JpaRepository<AccessDriftFinding, UUID> {

    List<AccessDriftFinding> findBySnapshotIdAndStatus(UUID snapshotId, DriftFindingStatus status);

    @Query("SELECT f FROM AccessDriftFinding f WHERE f.snapshot.directory.id = :directoryId AND f.status = :status ORDER BY f.severity, f.detectedAt DESC")
    List<AccessDriftFinding> findByDirectoryIdAndStatus(@Param("directoryId") UUID directoryId, @Param("status") DriftFindingStatus status);

    @Query("SELECT f FROM AccessDriftFinding f WHERE f.snapshot.directory.id = :directoryId ORDER BY f.severity, f.detectedAt DESC")
    List<AccessDriftFinding> findByDirectoryId(@Param("directoryId") UUID directoryId);

    @Query("SELECT COUNT(f) FROM AccessDriftFinding f WHERE f.snapshot.directory.id = :directoryId AND f.status = :status AND f.severity = :severity")
    long countByDirectoryIdAndStatusAndSeverity(@Param("directoryId") UUID directoryId,
                                                 @Param("status") DriftFindingStatus status,
                                                 @Param("severity") DriftFindingSeverity severity);

    @Query("SELECT COUNT(f) FROM AccessDriftFinding f WHERE f.snapshot.directory.id = :directoryId AND f.status = :status")
    long countByDirectoryIdAndStatus(@Param("directoryId") UUID directoryId, @Param("status") DriftFindingStatus status);

    @Query("SELECT f FROM AccessDriftFinding f WHERE f.rule.id = :ruleId AND LOWER(f.userDn) = LOWER(:userDn) AND f.groupDn = :groupDn AND f.status = :status")
    List<AccessDriftFinding> findExisting(@Param("ruleId") UUID ruleId, @Param("userDn") String userDn,
                                           @Param("groupDn") String groupDn, @Param("status") DriftFindingStatus status);
}
