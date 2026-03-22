package com.ldapadmin.repository;

import com.ldapadmin.entity.AccessReviewCampaignHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccessReviewCampaignHistoryRepository extends JpaRepository<AccessReviewCampaignHistory, UUID> {

    List<AccessReviewCampaignHistory> findByCampaignIdOrderByChangedAtAsc(UUID campaignId);
}
