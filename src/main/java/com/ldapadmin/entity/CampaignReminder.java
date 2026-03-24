package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.ReminderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaign_reminders")
@Getter
@Setter
@NoArgsConstructor
public class CampaignReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id")
    private AccessReviewCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_account_id")
    private Account reviewerAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type")
    private ReminderType reminderType;

    private OffsetDateTime sentAt;

    public CampaignReminder(AccessReviewCampaign campaign, Account reviewer, ReminderType type) {
        this.campaign = campaign;
        this.reviewerAccount = reviewer;
        this.reminderType = type;
        this.sentAt = OffsetDateTime.now();
    }
}
