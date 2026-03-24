package com.ldapadmin.dto.sod;

import jakarta.validation.constraints.NotBlank;

public record ExemptViolationRequest(
        @NotBlank String reason
) {}
