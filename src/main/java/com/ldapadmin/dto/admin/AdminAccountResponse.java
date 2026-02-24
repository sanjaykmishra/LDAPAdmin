package com.ldapadmin.dto.admin;

import com.ldapadmin.entity.AdminAccount;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAccountResponse(
        UUID id,
        UUID tenantId,
        String username,
        String displayName,
        String email,
        boolean active,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AdminAccountResponse from(AdminAccount a) {
        return new AdminAccountResponse(
                a.getId(),
                a.getTenant().getId(),
                a.getUsername(),
                a.getDisplayName(),
                a.getEmail(),
                a.isActive(),
                a.getLastLoginAt(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
