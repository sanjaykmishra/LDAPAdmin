package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable template definition associated with one or more LDAP objectClasses.
 * <p>
 * Multiple templates may exist for the same set of objectClasses (distinguished by
 * {@code templateName}), allowing different realms to use different field
 * configurations for the same structural class.
 * <p>
 * Attribute-level behaviour is defined in {@link UserTemplateAttributeConfig}.
 * Realms reference a template via {@link RealmObjectclass#getUserTemplate()}.
 */
@Entity
@Table(name = "user_template")
@Getter
@Setter
@NoArgsConstructor
public class UserTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Optional directory connection this template is scoped to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "directory_id")
    private DirectoryConnection directoryConnection;

    /** LDAP objectClasses this template is designed for. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_template_object_classes",
        joinColumns = @JoinColumn(name = "user_template_id")
    )
    @Column(name = "object_class_name")
    private List<String> objectClassNames = new ArrayList<>();

    /** Human-readable name that distinguishes this template from others. */
    @Column(name = "template_name", nullable = false)
    private String templateName;
}
