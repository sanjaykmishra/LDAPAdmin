package com.ldapadmin.repository;

import com.ldapadmin.entity.AccessReviewGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccessReviewGroupRepository extends JpaRepository<AccessReviewGroup, UUID> {

    List<AccessReviewGroup> findByCampaignId(UUID campaignId);

    List<AccessReviewGroup> findByCampaignIdAndReviewerId(UUID campaignId, UUID reviewerId);
}
