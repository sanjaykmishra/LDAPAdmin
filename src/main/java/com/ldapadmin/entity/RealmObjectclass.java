package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * An LDAP objectClass whose attribute configuration forms the user entry form
 * for a {@link Realm}.  Replaces the former {@code directory_objectclasses} model,
 * re-scoping objectclass configuration from directory level to realm level.
 * <p>
 * Attribute-level behaviour is defined in {@link ObjectclassAttributeConfig}.
 */
@Entity
@Table(
    name = "realm_objectclasses",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_realm_objectclass",
        columnNames = {"realm_id", "object_class_name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class RealmObjectclass {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "realm_id", nullable = false)
    private Realm realm;

    /** Exact objectClass name as it appears in the LDAP schema. */
    @Column(name = "object_class_name", nullable = false)
    private String objectClassName;

    /** Human-readable label shown in the UI instead of the raw objectClass name. */
    @Column(name = "display_name")
    private String displayName;

    /** Controls the order of objectclasses in the user form. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
