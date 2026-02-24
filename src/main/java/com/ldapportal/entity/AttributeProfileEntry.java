package com.ldapportal.entity;

import com.ldapportal.entity.enums.InputType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-attribute configuration row within an {@link AttributeProfile} (§5.2).
 */
@Entity
@Table(
    name = "attribute_profile_entries",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_profile_entry_attr",
        columnNames = {"profile_id", "attribute_name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class AttributeProfileEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private AttributeProfile profile;

    /** LDAP attribute name as returned by schema discovery. */
    @Column(name = "attribute_name", nullable = false)
    private String attributeName;

    /** Override label shown in the UI (replaces raw attribute name). */
    @Column(name = "custom_label")
    private String customLabel;

    /** Field is mandatory when creating a new user entry. */
    @Column(name = "required_on_create", nullable = false)
    private boolean requiredOnCreate = false;

    /** If {@code false} the field is shown read-only in edit forms. */
    @Column(name = "enabled_on_edit", nullable = false)
    private boolean enabledOnEdit = true;

    /** Presentation hint. Default inferred from LDAP syntax; admin may override. */
    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 20)
    private InputType inputType = InputType.TEXT;

    /** Controls column order in forms and list views. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** Whether this attribute appears as a column in user search results. */
    @Column(name = "visible_in_list_view", nullable = false)
    private boolean visibleInListView = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
