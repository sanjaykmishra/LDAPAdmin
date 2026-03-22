package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.ReviewDecision;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "access_review_decisions")
@Getter
@Setter
@NoArgsConstructor
public class AccessReviewDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_group_id")
    private AccessReviewGroup reviewGroup;

    private String memberDn;
    private String memberDisplay;

    @Enumerated(EnumType.STRING)
    private ReviewDecision decision;

    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private Account decidedBy;

    private OffsetDateTime decidedAt;
    private OffsetDateTime revokedAt;
}
