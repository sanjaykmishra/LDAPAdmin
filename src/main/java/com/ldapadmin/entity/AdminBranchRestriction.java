package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Permission model Dimension 3 (§3.2).
 * <p>
 * Restricts an admin's scope within a directory to specific OU/branch DNs.
 * No rows for a given (admin, directory) pair = full directory access
 * within the admin's assigned base role.
 */
@Entity
@Table(
    name = "admin_branch_restrictions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_admin_branch",
        columnNames = {"admin_account_id", "directory_id", "branch_dn"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class AdminBranchRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_account_id", nullable = false)
    private AdminAccount adminAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id", nullable = false)
    private DirectoryConnection directory;

    @Column(name = "branch_dn", nullable = false)
    private String branchDn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
