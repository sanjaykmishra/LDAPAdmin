package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Associates a {@link Realm} with an LDAP objectClass and the {@link UserForm}
 * used to create and edit user entries of that objectClass within the realm.
 */
@Entity
@Table(name = "realm_objectclasses")
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

    /**
     * Opaque UUID reference to the objectClass this row represents.
     * May be used by the application to look up LDAP schema information.
     */
    @Column(name = "object_class_id")
    private UUID objectClassId;

    /** The form definition used to create and edit user entries for this objectClass. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_form_id")
    private UserForm userForm;
}
