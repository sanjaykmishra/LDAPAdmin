package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.SodViolationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sod_violations")
@Getter
@Setter
@NoArgsConstructor
public class SodViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id")
    private SodPolicy policy;

    private String userDn;
    private String userDisplayName;

    private OffsetDateTime detectedAt;
    private OffsetDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    private SodViolationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exempted_by")
    private Account exemptedBy;

    private String exemptionReason;
    private OffsetDateTime exemptionExpiresAt;
}
