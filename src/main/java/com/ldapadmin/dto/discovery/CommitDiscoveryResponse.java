package com.ldapadmin.dto.discovery;

import java.util.List;

/**
 * Result of committing a discovery proposal.
 */
public record CommitDiscoveryResponse(
        int profilesCreated,
        int userBaseDnsAdded,
        int groupBaseDnsAdded,
        List<String> warnings
) {}
