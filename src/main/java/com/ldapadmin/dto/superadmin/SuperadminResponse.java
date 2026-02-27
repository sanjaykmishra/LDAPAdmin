package com.ldapadmin.dto.superadmin;

import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.enums.AccountType;

import java.time.Instant;
import java.util.UUID;

/** Superadmin response — password hash is never included. */
public record SuperadminResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        AccountType authType,
        boolean active,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static SuperadminResponse from(Account a) {
        return new SuperadminResponse(
                a.getId(),
                a.getUsername(),
                a.getDisplayName(),
                a.getEmail(),
                a.getAuthType(),
                a.isActive(),
                a.getLastLoginAt(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
