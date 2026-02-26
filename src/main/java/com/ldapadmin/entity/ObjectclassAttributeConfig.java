package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.InputType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Specifies how a single LDAP attribute within a {@link RealmObjectclass} is
 * presented and validated in the user creation and edit forms.
 */
@Entity
@Table(
    name = "objectclass_attribute_configs",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_objectclass_attribute",
        columnNames = {"objectclass_id", "attribute_name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class ObjectclassAttributeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "objectclass_id", nullable = false)
    private RealmObjectclass objectclass;

    /** LDAP attribute name as returned by schema discovery. */
    @Column(name = "attribute_name", nullable = false)
    private String attributeName;

    /** Override label shown in the UI; falls back to {@code attributeName} when null. */
    @Column(name = "custom_label")
    private String customLabel;

    /** Whether the attribute must be supplied when creating a new user entry. */
    @Column(name = "required_on_create", nullable = false)
    private boolean requiredOnCreate = false;

    /** When {@code false} the field is rendered read-only in the edit form. */
    @Column(name = "editable_on_edit", nullable = false)
    private boolean editableOnEdit = true;

    /** Presentation hint that drives the input widget rendered by the frontend. */
    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 20)
    private InputType inputType = InputType.TEXT;

    /** Controls the order of attributes within a form or list view. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** When {@code true} the attribute appears as a column in the user search results list. */
    @Column(name = "visible_in_list", nullable = false)
    private boolean visibleInList = false;
}
