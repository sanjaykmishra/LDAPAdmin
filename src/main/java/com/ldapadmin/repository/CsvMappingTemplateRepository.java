package com.ldapadmin.repository;

import com.ldapadmin.entity.CsvMappingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CsvMappingTemplateRepository extends JpaRepository<CsvMappingTemplate, UUID> {

    List<CsvMappingTemplate> findAllByDirectoryId(UUID directoryId);

    List<CsvMappingTemplate> findAllByTenantId(UUID tenantId);

    Optional<CsvMappingTemplate> findByDirectoryIdAndName(UUID directoryId, String name);

    Optional<CsvMappingTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByDirectoryIdAndName(UUID directoryId, String name);
}
