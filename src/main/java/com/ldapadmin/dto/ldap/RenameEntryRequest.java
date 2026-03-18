package com.ldapadmin.dto.ldap;

import jakarta.validation.constraints.NotBlank;

public record RenameEntryRequest(@NotBlank String newRdn) {
}
