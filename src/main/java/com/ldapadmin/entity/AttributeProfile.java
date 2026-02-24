package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Attribute profile for a specific OU/branch within a directory (§5.2).
 * <p>
 * Each (directory, branchDn) pair has at most one profile (unique constraint).
 * The directory-level default profile uses {@code branchDn = "*"} and
 * {@code isDefault = true}.  A partial unique index in the DB enforces
 * at most one default profile per directory.
 */
@Entity
@Table(
    name = "attribute_profiles",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_profile_dir_branch",
        columnNames = {"directory_id", "branch_dn"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class AttributeProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id", nullable = false)
    private DirectoryConnection directory;

    /**
     * OU DN this profile applies to.
     * The reserved value {@code "*"} designates the directory-level default profile.
     */
    @Column(name = "branch_dn", nullable = false)
    private String branchDn;

    @Column(name = "display_name")
    private String displayName;

    /**
     * {@code true} for the directory-level fallback profile.
     * At most one default per directory (enforced by partial unique index).
     */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
