package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "access_snapshot_memberships")
@Getter
@Setter
@NoArgsConstructor
public class AccessSnapshotMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id")
    private AccessSnapshot snapshot;

    private String userDn;
    private String groupDn;
    private String groupName;
}
