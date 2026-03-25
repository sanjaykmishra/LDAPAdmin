package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.SodAction;
import com.ldapadmin.entity.enums.SodSeverity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sod_policies")
@Getter
@Setter
@NoArgsConstructor
public class SodPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id")
    private DirectoryConnection directory;

    @Column(name = "group_a_dn")
    private String groupADn;

    @Column(name = "group_b_dn")
    private String groupBDn;

    @Column(name = "group_a_name")
    private String groupAName;

    @Column(name = "group_b_name")
    private String groupBName;

    @Enumerated(EnumType.STRING)
    private SodSeverity severity;

    @Enumerated(EnumType.STRING)
    private SodAction action;

    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Account createdBy;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
