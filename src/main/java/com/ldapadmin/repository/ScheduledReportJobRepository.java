package com.ldapadmin.repository;

import com.ldapadmin.entity.ScheduledReportJob;
import com.ldapadmin.entity.enums.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledReportJobRepository extends JpaRepository<ScheduledReportJob, UUID> {

    Page<ScheduledReportJob> findAllByDirectoryId(UUID directoryId, Pageable pageable);

    List<ScheduledReportJob> findAllByDirectoryId(UUID directoryId);

    List<ScheduledReportJob> findAllByEnabledTrue();

    List<ScheduledReportJob> findAllByDirectoryIdAndReportType(UUID directoryId, ReportType reportType);
}
