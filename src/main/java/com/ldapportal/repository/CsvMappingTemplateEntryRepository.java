package com.ldapportal.repository;

import com.ldapportal.entity.CsvMappingTemplateEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CsvMappingTemplateEntryRepository extends JpaRepository<CsvMappingTemplateEntry, UUID> {

    List<CsvMappingTemplateEntry> findAllByTemplateId(UUID templateId);

    Optional<CsvMappingTemplateEntry> findByTemplateIdAndCsvColumnName(UUID templateId, String csvColumnName);

    void deleteAllByTemplateId(UUID templateId);
}
