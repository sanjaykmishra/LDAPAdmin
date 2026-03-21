package com.ldapadmin.repository;

import com.ldapadmin.entity.PendingApproval;
import com.ldapadmin.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PendingApprovalRepository extends JpaRepository<PendingApproval, UUID> {

    List<PendingApproval> findAllByRealmIdAndStatus(UUID realmId, ApprovalStatus status);

    List<PendingApproval> findAllByDirectoryIdAndStatus(UUID directoryId, ApprovalStatus status);

    List<PendingApproval> findAllByDirectoryIdOrderByCreatedAtDesc(UUID directoryId);

    List<PendingApproval> findAllByRequestedByOrderByCreatedAtDesc(UUID requestedBy);

    long countByRealmIdAndStatus(UUID realmId, ApprovalStatus status);

    long countByDirectoryIdAndStatus(UUID directoryId, ApprovalStatus status);
}
