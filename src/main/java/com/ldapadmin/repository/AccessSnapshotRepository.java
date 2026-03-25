package com.ldapadmin.repository;

import com.ldapadmin.entity.AccessSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessSnapshotRepository extends JpaRepository<AccessSnapshot, UUID> {

    List<AccessSnapshot> findByDirectoryIdOrderByCapturedAtDesc(UUID directoryId);

    Optional<AccessSnapshot> findFirstByDirectoryIdOrderByCapturedAtDesc(UUID directoryId);
}
