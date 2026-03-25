package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.DriftFindingSeverity;
import com.ldapadmin.entity.enums.DriftFindingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "access_drift_findings")
@Getter
@Setter
@NoArgsConstructor
public class AccessDriftFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id")
    private AccessSnapshot snapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id")
    private PeerGroupRule rule;

    private String userDn;
    private String userDisplay;
    private String peerGroupValue;
    private int peerGroupSize;
    private String groupDn;
    private String groupName;
    private double peerMembershipPct;

    @Enumerated(EnumType.STRING)
    private DriftFindingSeverity severity;

    @Enumerated(EnumType.STRING)
    private DriftFindingStatus status = DriftFindingStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private Account acknowledgedBy;

    private OffsetDateTime acknowledgedAt;
    private String exemptionReason;
    private OffsetDateTime detectedAt;
}
