package com.ldapadmin.repository;

import com.ldapadmin.entity.CampaignReminder;
import com.ldapadmin.entity.enums.ReminderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignReminderRepository extends JpaRepository<CampaignReminder, UUID> {

    boolean existsByCampaignIdAndReviewerAccountIdAndReminderType(UUID campaignId, UUID reviewerAccountId, ReminderType reminderType);

    boolean existsByCampaignIdAndReviewerAccountIdAndReminderTypeAndSentAtAfter(
            UUID campaignId, UUID reviewerAccountId, ReminderType reminderType, OffsetDateTime after);

    List<CampaignReminder> findByCampaignIdOrderBySentAtDesc(UUID campaignId);
}
