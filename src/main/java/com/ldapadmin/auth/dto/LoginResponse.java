package com.ldapadmin.auth.dto;

/**
 * Successful login response carrying the signed JWT and basic principal info.
 */
public record LoginResponse(
        String token,
        String username,
        String accountType) {
}
