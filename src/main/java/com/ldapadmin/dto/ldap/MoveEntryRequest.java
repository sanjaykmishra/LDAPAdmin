package com.ldapadmin.dto.ldap;

import jakarta.validation.constraints.NotBlank;

public record MoveEntryRequest(@NotBlank String newParentDn) {
}
