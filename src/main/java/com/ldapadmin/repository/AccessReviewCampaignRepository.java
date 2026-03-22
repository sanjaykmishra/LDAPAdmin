package com.ldapadmin.repository;

import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.enums.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccessReviewCampaignRepository extends JpaRepository<AccessReviewCampaign, UUID> {

    Page<AccessReviewCampaign> findByDirectoryId(UUID directoryId, Pageable pageable);

    List<AccessReviewCampaign> findByDirectoryIdAndStatus(UUID directoryId, CampaignStatus status);

    List<AccessReviewCampaign> findByStatusAndDeadlineBefore(CampaignStatus status, OffsetDateTime deadline);
}
