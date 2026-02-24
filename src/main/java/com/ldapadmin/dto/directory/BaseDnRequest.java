package com.ldapadmin.dto.directory;

import jakarta.validation.constraints.NotBlank;

public record BaseDnRequest(
        @NotBlank String dn,
        int displayOrder) {
}
