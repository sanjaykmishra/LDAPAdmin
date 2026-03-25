package com.ldapadmin.repository.hr;

import com.ldapadmin.entity.hr.HrConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HrConnectionRepository extends JpaRepository<HrConnection, UUID> {

    Optional<HrConnection> findByDirectoryId(UUID directoryId);

    List<HrConnection> findByEnabledTrue();
}
