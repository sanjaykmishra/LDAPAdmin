package com.ldapadmin.repository;

import com.ldapadmin.entity.AccessSnapshotMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccessSnapshotMembershipRepository extends JpaRepository<AccessSnapshotMembership, UUID> {

    List<AccessSnapshotMembership> findBySnapshotId(UUID snapshotId);

    @Query("SELECT DISTINCT m.userDn FROM AccessSnapshotMembership m WHERE m.snapshot.id = :snapshotId")
    List<String> findDistinctUserDnsBySnapshotId(@Param("snapshotId") UUID snapshotId);

    @Query("SELECT m.groupDn FROM AccessSnapshotMembership m WHERE m.snapshot.id = :snapshotId AND LOWER(m.userDn) = LOWER(:userDn)")
    List<String> findGroupDnsBySnapshotIdAndUserDn(@Param("snapshotId") UUID snapshotId, @Param("userDn") String userDn);

    long countBySnapshotId(UUID snapshotId);
}
