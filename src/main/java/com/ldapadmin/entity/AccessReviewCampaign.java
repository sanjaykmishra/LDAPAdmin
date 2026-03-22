package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.CampaignStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "access_review_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class AccessReviewCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id")
    private DirectoryConnection directory;

    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private CampaignStatus status = CampaignStatus.UPCOMING;

    private OffsetDateTime startsAt;
    private OffsetDateTime deadline;
    private Integer deadlineDays;
    private boolean autoRevoke;
    private boolean autoRevokeOnExpiry;
    private Integer recurrenceMonths;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_campaign_id")
    private AccessReviewCampaign sourceCampaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Account createdBy;

    @CreationTimestamp
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccessReviewGroup> reviewGroups = new ArrayList<>();
}
