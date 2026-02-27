package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A reusable form definition associated with a specific LDAP objectClass.
 * <p>
 * Multiple forms may exist for the same objectClass (distinguished by
 * {@code formName}), allowing different realms to use different field
 * configurations for the same structural class.
 * <p>
 * Attribute-level behaviour is defined in {@link UserFormAttributeConfig}.
 * Realms reference a form via {@link RealmObjectclass#getUserForm()}.
 */
@Entity
@Table(name = "user_form")
@Getter
@Setter
@NoArgsConstructor
public class UserForm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** LDAP objectClass this form is designed for. */
    @Column(name = "object_class_name", nullable = false)
    private String objectClassName;

    /** Human-readable name that distinguishes this form from others for the same objectClass. */
    @Column(name = "form_name", nullable = false)
    private String formName;
}
