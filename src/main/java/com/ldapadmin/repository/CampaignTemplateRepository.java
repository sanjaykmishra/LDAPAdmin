package com.ldapadmin.repository;

import com.ldapadmin.entity.CampaignTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignTemplateRepository extends JpaRepository<CampaignTemplate, UUID> {
    List<CampaignTemplate> findByDirectoryIdOrderByCreatedAtDesc(UUID directoryId);
}
