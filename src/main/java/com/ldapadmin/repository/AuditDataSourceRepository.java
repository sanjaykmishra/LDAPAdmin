package com.ldapadmin.repository;

import com.ldapadmin.entity.AuditDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditDataSourceRepository extends JpaRepository<AuditDataSource, UUID> {

    List<AuditDataSource> findAllByEnabledTrue();
}
