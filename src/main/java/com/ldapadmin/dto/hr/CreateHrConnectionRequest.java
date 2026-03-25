package com.ldapadmin.dto.hr;

import jakarta.validation.constraints.NotBlank;

public record CreateHrConnectionRequest(
        @NotBlank String displayName,
        @NotBlank String subdomain,
        @NotBlank String apiKey,
        String matchAttribute,
        String matchField,
        String syncCron
) {}
