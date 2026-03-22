package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.CampaignStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "access_review_campaign_history")
@Getter
@Setter
@NoArgsConstructor
public class AccessReviewCampaignHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id")
    private AccessReviewCampaign campaign;

    @Enumerated(EnumType.STRING)
    private CampaignStatus oldStatus;

    @Enumerated(EnumType.STRING)
    private CampaignStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by")
    private Account changedBy;

    @CreationTimestamp
    private OffsetDateTime changedAt;

    private String note;
}
