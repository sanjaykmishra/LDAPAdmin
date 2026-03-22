package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "access_review_groups")
@Getter
@Setter
@NoArgsConstructor
public class AccessReviewGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id")
    private AccessReviewCampaign campaign;

    private String groupDn;
    private String groupName;
    private String memberAttribute;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id")
    private Account reviewer;

    @OneToMany(mappedBy = "reviewGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccessReviewDecision> decisions = new ArrayList<>();
}
