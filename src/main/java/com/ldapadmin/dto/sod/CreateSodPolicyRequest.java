package com.ldapadmin.dto.sod;

import com.ldapadmin.entity.enums.SodAction;
import com.ldapadmin.entity.enums.SodSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSodPolicyRequest(
        @NotBlank String name,
        String description,
        @NotBlank String groupADn,
        @NotBlank String groupBDn,
        String groupAName,
        String groupBName,
        @NotNull SodSeverity severity,
        @NotNull SodAction action,
        boolean enabled
) {}
