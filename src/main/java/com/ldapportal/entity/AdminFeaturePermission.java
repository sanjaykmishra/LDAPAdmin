package com.ldapportal.entity;

import com.ldapportal.entity.enums.FeatureKey;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Permission model Dimension 4 (§3.2).
 * <p>
 * Per-feature enable/disable overrides for an admin account.
 * A row here overrides the capability that the admin's base role would
 * normally grant or deny.  The {@link FeatureKey} enum constants are stored
 * as dot-notation strings via {@link com.ldapportal.entity.converter.FeatureKeyConverter}.
 */
@Entity
@Table(
    name = "admin_feature_permissions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_admin_feature",
        columnNames = {"admin_account_id", "feature_key"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class AdminFeaturePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_account_id", nullable = false)
    private AdminAccount adminAccount;

    /**
     * Stored as dot-notation string by {@link com.ldapportal.entity.converter.FeatureKeyConverter},
     * e.g. {@code "user.create"}.
     */
    @Column(name = "feature_key", nullable = false, length = 100)
    private FeatureKey featureKey;

    /** {@code true} = feature enabled; {@code false} = explicitly disabled. */
    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
