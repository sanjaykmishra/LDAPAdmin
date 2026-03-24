package com.ldapadmin.dto.accessreview;

import com.ldapadmin.entity.enums.ReminderType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CampaignReminderDto(
        UUID id,
        ReminderType reminderType,
        String reviewerUsername,
        UUID reviewerAccountId,
        OffsetDateTime sentAt
) {}
