package com.ldapadmin.dto.csv;

import com.ldapadmin.entity.enums.ConflictHandling;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for creating or replacing a {@link com.ldapadmin.entity.CsvMappingTemplate}.
 */
public record CreateCsvMappingTemplateRequest(
        @NotBlank String name,
        /** LDAP attribute used to match CSV rows against existing directory entries (default: uid). */
        String targetKeyAttribute,
        /** Default conflict resolution when a matching entry already exists. */
        ConflictHandling conflictHandling,
        @NotNull @Valid List<CsvColumnMappingDto> entries) {
}
