package com.ldapadmin.repository;

import com.ldapadmin.entity.AccessReviewDecision;
import com.ldapadmin.entity.enums.ReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccessReviewDecisionRepository extends JpaRepository<AccessReviewDecision, UUID> {

    List<AccessReviewDecision> findByReviewGroupId(UUID reviewGroupId);

    long countByReviewGroupIdAndDecisionIsNull(UUID reviewGroupId);

    @Query("SELECT COUNT(d) FROM AccessReviewDecision d WHERE d.reviewGroup.campaign.id = :campaignId AND d.decision IS NULL")
    long countPendingByCampaignId(@Param("campaignId") UUID campaignId);

    @Query("SELECT COUNT(d) FROM AccessReviewDecision d WHERE d.reviewGroup.campaign.id = :campaignId")
    long countTotalByCampaignId(@Param("campaignId") UUID campaignId);

    @Query("SELECT COUNT(d) FROM AccessReviewDecision d WHERE d.reviewGroup.campaign.id = :campaignId AND d.decision = :decision")
    long countByCampaignIdAndDecision(@Param("campaignId") UUID campaignId, @Param("decision") ReviewDecision decision);

    @Query("SELECT d FROM AccessReviewDecision d WHERE d.reviewGroup.campaign.id = :campaignId AND d.decision = :decision")
    List<AccessReviewDecision> findByCampaignIdAndDecision(@Param("campaignId") UUID campaignId, @Param("decision") ReviewDecision decision);

    @Query("SELECT d FROM AccessReviewDecision d WHERE d.reviewGroup.campaign.id IN :campaignIds AND d.decision IS NOT NULL")
    List<AccessReviewDecision> findDecidedByCampaignIds(@Param("campaignIds") List<UUID> campaignIds);
}
