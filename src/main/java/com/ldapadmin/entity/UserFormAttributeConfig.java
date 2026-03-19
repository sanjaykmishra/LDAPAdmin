package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.InputType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Specifies how a single LDAP attribute within a {@link UserForm} is presented
 * and validated in the user creation and edit forms.
 */
@Entity
@Table(
    name = "user_form_attribute_config",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_user_form_attribute",
        columnNames = {"user_form_id", "attribute_name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class UserFormAttributeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_form_id", nullable = false)
    private UserForm userForm;

    /** LDAP attribute name as returned by schema discovery. */
    @Column(name = "attribute_name", nullable = false)
    private String attributeName;

    /** Override label shown in the UI; falls back to {@code attributeName} when null. */
    @Column(name = "custom_label")
    private String customLabel;

    /** Whether the attribute must be supplied when creating a new user entry. */
    @Column(name = "required_on_create", nullable = false)
    private boolean requiredOnCreate = false;

    /** When {@code false} the field is rendered read-only in the creation form. */
    @Column(name = "editable_on_create", nullable = false)
    private boolean editableOnCreate = true;

    /** Presentation hint that drives the input widget rendered by the frontend. */
    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 20)
    private InputType inputType = InputType.TEXT;

    /** Whether this attribute is used as the RDN (Relative Distinguished Name) for user entries. */
    @Column(name = "is_rdn", nullable = false)
    private boolean rdn = false;

    /** Optional section/group name. Attributes sharing the same section are visually grouped. */
    @Column(name = "section_name")
    private String sectionName;

    /** Number of grid columns this field spans (1–3 in a 3-column grid). Default 3 = full width. */
    @Column(name = "column_span", nullable = false)
    private int columnSpan = 3;
}
