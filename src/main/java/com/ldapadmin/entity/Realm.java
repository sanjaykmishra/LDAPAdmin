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
 * A realm is a logical partition of a {@link DirectoryConnection} that defines:
 * <ul>
 *   <li>the LDAP subtrees searched for user and group entries,</li>
 *   <li>the user forms available for creating/editing user entries
 *       (linked via {@link RealmObjectclass}).</li>
 * </ul>
 * Admin permissions ({@link AdminRealmRole}) are scoped
 * to realms rather than to directories.
 */
@Entity
@Table(name = "realms")
@Getter
@Setter
@NoArgsConstructor
public class Realm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id", nullable = false)
    private DirectoryConnection directory;

    @Column(nullable = false)
    private String name;

    /** LDAP DN used as the search base for user entries in this realm. */
    @Column(name = "user_base_dn", nullable = false)
    private String userBaseDn;

    /** LDAP DN used as the search base for group entries in this realm. */
    @Column(name = "group_base_dn", nullable = false)
    private String groupBaseDn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
