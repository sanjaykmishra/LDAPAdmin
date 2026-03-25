package com.ldapadmin.dto.sod;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public record ExemptViolationRequest(
        @NotBlank String reason,
        OffsetDateTime expiresAt
) {}
