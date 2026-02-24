-- V1: Root tenant table.
-- Every other record in the application belongs to a tenant.

CREATE TABLE tenants (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    -- URL-safe unique identifier used in routing and logging
    slug       VARCHAR(100) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_tenants PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_tenants_slug ON tenants (slug);
