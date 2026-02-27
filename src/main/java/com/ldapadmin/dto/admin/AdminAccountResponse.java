package com.ldapadmin.dto.admin;

import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.enums.AccountType;

import java.time.Instant;
import java.util.UUID;

public record AdminAccountResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        AccountType authType,
        boolean active,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminAccountResponse from(Account a) {
        return new AdminAccountResponse(
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
