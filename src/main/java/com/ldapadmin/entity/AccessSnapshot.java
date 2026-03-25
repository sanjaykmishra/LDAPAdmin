package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.SnapshotStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "access_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class AccessSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id")
    private DirectoryConnection directory;

    private OffsetDateTime capturedAt;

    @Enumerated(EnumType.STRING)
    private SnapshotStatus status = SnapshotStatus.IN_PROGRESS;

    private Integer totalUsers;
    private Integer totalGroups;
    private String errorMessage;
    private OffsetDateTime completedAt;
}
