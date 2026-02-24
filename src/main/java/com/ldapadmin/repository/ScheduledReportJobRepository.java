package com.ldapadmin.repository;

import com.ldapadmin.entity.ScheduledReportJob;
import com.ldapadmin.entity.enums.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduledReportJobRepository extends JpaRepository<ScheduledReportJob, UUID> {

    Page<ScheduledReportJob> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<ScheduledReportJob> findAllByTenantIdAndEnabledTrue(UUID tenantId);

    List<ScheduledReportJob> findAllByDirectoryId(UUID directoryId);

    List<ScheduledReportJob> findAllByTenantIdAndDirectoryId(UUID tenantId, UUID directoryId);

    Optional<ScheduledReportJob> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ScheduledReportJob> findAllByEnabledTrue();

    List<ScheduledReportJob> findAllByTenantIdAndReportType(UUID tenantId, ReportType reportType);
}
