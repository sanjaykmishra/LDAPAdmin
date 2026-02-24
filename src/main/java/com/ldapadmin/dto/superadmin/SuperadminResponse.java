package com.ldapadmin.dto.superadmin;

import com.ldapadmin.entity.SuperadminAccount;
import com.ldapadmin.entity.enums.AccountType;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Superadmin response — password hash is never included. */
public record SuperadminResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        AccountType accountType,
        boolean active,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static SuperadminResponse from(SuperadminAccount a) {
        return new SuperadminResponse(
                a.getId(),
                a.getUsername(),
                a.getDisplayName(),
                a.getEmail(),
                a.getAccountType(),
                a.isActive(),
                a.getLastLoginAt(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
