package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A provisioning profile defines how users of a particular type are created,
 * managed, and governed within a directory connection.
 *
 * <p>Replaces the former {@link Realm} + {@link UserTemplate} combination,
 * unifying identity provisioning, access assignment, lifecycle policy,
 * approval workflow, and form layout into a single manageable unit.</p>
 */
@Entity
@Table(
    name = "provisioning_profiles",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_profile_directory_name",
        columnNames = {"directory_id", "name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class ProvisioningProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id", nullable = false)
    private DirectoryConnection directory;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    /** LDAP DN of the OU where new users are created. */
    @Column(name = "target_ou_dn", nullable = false, length = 500)
    private String targetOuDn;

    /** LDAP objectClasses applied to entries created with this profile. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "profile_object_classes",
        joinColumns = @JoinColumn(name = "provisioning_profile_id")
    )
    @Column(name = "object_class_name")
    private List<String> objectClassNames = new ArrayList<>();

    /** The attribute used as the RDN when constructing the entry DN. */
    @Column(name = "rdn_attribute", nullable = false, length = 100)
    private String rdnAttribute;

    @Column(name = "show_dn_field", nullable = false)
    private boolean showDnField = true;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "self_registration_allowed", nullable = false)
    private boolean selfRegistrationAllowed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
