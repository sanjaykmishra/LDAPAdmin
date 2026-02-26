package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.BaseRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Permission model Dimensions 1 + 2 — realm-scoped (§3.2).
 * <p>
 * Assigns a base role to an admin account for a specific {@link Realm}.
 * An admin may have different roles across different realms.
 * Absence of a row for a given (admin, realm) pair = access denied to that realm.
 * <p>
 * Replaces the former {@code admin_directory_roles} relationship, re-scoping
 * permissions from directory level to realm level.
 */
@Entity
@Table(
    name = "admin_realm_roles",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_admin_realm_role",
        columnNames = {"admin_account_id", "realm_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class AdminRealmRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_account_id", nullable = false)
    private AdminAccount adminAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "realm_id", nullable = false)
    private Realm realm;

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
