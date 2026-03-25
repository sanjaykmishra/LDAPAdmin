package com.ldapadmin.repository.hr;

import com.ldapadmin.entity.hr.HrSyncRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HrSyncRunRepository extends JpaRepository<HrSyncRun, UUID> {

    Page<HrSyncRun> findByHrConnectionIdOrderByStartedAtDesc(UUID hrConnectionId, Pageable pageable);

    Optional<HrSyncRun> findTopByHrConnectionIdOrderByStartedAtDesc(UUID hrConnectionId);
}
