package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable form definition associated with one or more LDAP objectClasses.
 * <p>
 * Multiple forms may exist for the same set of objectClasses (distinguished by
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

    /** Optional directory connection this form is scoped to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "directory_id")
    private DirectoryConnection directoryConnection;

    /** LDAP objectClasses this form is designed for. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_form_object_classes",
        joinColumns = @JoinColumn(name = "user_form_id")
    )
    @Column(name = "object_class_name")
    private List<String> objectClassNames = new ArrayList<>();

    /** Human-readable name that distinguishes this form from others. */
    @Column(name = "form_name", nullable = false)
    private String formName;
}
