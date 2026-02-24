package com.ldapportal.entity;

import com.ldapportal.entity.enums.BaseRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Permission model Dimensions 1 + 2 (§3.2).
 * <p>
 * Assigns a base role to an admin account for a specific directory connection.
 * An admin may have different roles on different directories.
 * Absence of a row for a given (admin, directory) pair = access denied.
 */
@Entity
@Table(
    name = "admin_directory_roles",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_admin_dir_role",
        columnNames = {"admin_account_id", "directory_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class AdminDirectoryRole {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "base_role", nullable = false, length = 20)
    private BaseRole baseRole;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
