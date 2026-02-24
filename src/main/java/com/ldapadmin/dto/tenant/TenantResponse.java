package com.ldapadmin.dto.tenant;

import com.ldapadmin.entity.Tenant;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String slug,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static TenantResponse from(Tenant t) {
        return new TenantResponse(
                t.getId(), t.getName(), t.getSlug(), t.isEnabled(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
