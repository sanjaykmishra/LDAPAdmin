package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * An auxiliary LDAP objectClass applied to user entries in a {@link Realm}
 * alongside the realm's {@link Realm#getPrimaryUserObjectclass() primary objectClass}.
 */
@Entity
@Table(
    name = "realm_auxiliary_objectclasses",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_realm_aux_objectclass",
        columnNames = {"realm_id", "objectclass_name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class RealmAuxiliaryObjectclass {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "realm_id", nullable = false)
    private Realm realm;

    @Column(name = "objectclass_name", nullable = false)
    private String objectclassName;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
