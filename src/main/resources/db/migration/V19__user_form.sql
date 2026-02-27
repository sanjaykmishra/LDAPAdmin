-- V19: Introduce user_form as a reusable, standalone form definition per
--      objectClass, and replace objectclass_attribute_configs with
--      user_form_attribute_config.  Modify realm_objectclasses to reference
--      a user_form instead of embedding objectClass metadata inline.
--
-- Changes:
--   user_form                  — new: standalone form template per objectClass
--   user_form_attribute_config — new: replaces objectclass_attribute_configs
--   objectclass_attribute_configs — dropped
--   realm_objectclasses        — replace object_class_name/display_name/display_order
--                                 with object_class_id (UUID) and user_form_id (FK)

-- ── 1. Create user_form ───────────────────────────────────────────────────────
-- A reusable form definition associated with a specific LDAP objectClass.
-- Multiple forms may exist for the same objectClass (e.g. different field sets
-- for different realms), identified by form_name.

CREATE TABLE user_form (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    object_class_name VARCHAR(255) NOT NULL,
    form_name         VARCHAR(255) NOT NULL,
    CONSTRAINT pk_user_form PRIMARY KEY (id)
);

-- ── 2. Create user_form_attribute_config ──────────────────────────────────────
-- Specifies how each LDAP attribute within a user_form is presented and
-- validated in the user creation and edit forms.

CREATE TABLE user_form_attribute_config (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_form_id       UUID         NOT NULL,
    attribute_name     VARCHAR(255) NOT NULL,
    custom_label       VARCHAR(255),
    required_on_create BOOLEAN      NOT NULL DEFAULT FALSE,
    editable_on_create BOOLEAN      NOT NULL DEFAULT TRUE,
    input_type         VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    CONSTRAINT pk_user_form_attribute_config PRIMARY KEY (id),
    CONSTRAINT uq_user_form_attribute        UNIQUE (user_form_id, attribute_name),
    CONSTRAINT fk_ufac_user_form
        FOREIGN KEY (user_form_id) REFERENCES user_form (id) ON DELETE CASCADE,
    CONSTRAINT chk_ufac_input_type CHECK (input_type IN (
        'TEXT', 'TEXTAREA', 'PASSWORD', 'BOOLEAN',
        'DATE', 'DATETIME', 'MULTI_VALUE', 'DN_LOOKUP'
    ))
);

CREATE INDEX idx_ufac_user_form ON user_form_attribute_config (user_form_id);

-- ── 3. Drop objectclass_attribute_configs ─────────────────────────────────────
-- This table held the FK to realm_objectclasses; drop it before altering that table.

DROP TABLE objectclass_attribute_configs;

-- ── 4. Modify realm_objectclasses ─────────────────────────────────────────────
-- Drop the inline objectClass metadata columns and replace them with
-- object_class_id (opaque UUID reference) and user_form_id (FK to user_form).

ALTER TABLE realm_objectclasses
    DROP CONSTRAINT uq_realm_objectclass,
    DROP COLUMN object_class_name,
    DROP COLUMN display_name,
    DROP COLUMN display_order,
    ADD COLUMN object_class_id UUID,
    ADD COLUMN user_form_id    UUID;

ALTER TABLE realm_objectclasses
    ADD CONSTRAINT fk_realm_oc_user_form
        FOREIGN KEY (user_form_id) REFERENCES user_form (id) ON DELETE SET NULL;
