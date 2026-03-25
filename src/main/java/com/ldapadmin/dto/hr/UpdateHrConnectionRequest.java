package com.ldapadmin.dto.hr;

public record UpdateHrConnectionRequest(
        String displayName,
        String subdomain,
        String apiKey,
        String matchAttribute,
        String matchField,
        String syncCron,
        Boolean enabled
) {}
