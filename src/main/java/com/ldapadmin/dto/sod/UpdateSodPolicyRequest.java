package com.ldapadmin.dto.sod;

import com.ldapadmin.entity.enums.SodAction;
import com.ldapadmin.entity.enums.SodSeverity;

public record UpdateSodPolicyRequest(
        String name,
        String description,
        String groupADn,
        String groupBDn,
        String groupAName,
        String groupBName,
        SodSeverity severity,
        SodAction action,
        Boolean enabled
) {}
