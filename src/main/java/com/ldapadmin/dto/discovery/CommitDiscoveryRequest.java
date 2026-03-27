package com.ldapadmin.dto.discovery;

import com.ldapadmin.dto.profile.CreateProfileRequest;

import java.util.List;

/**
 * Request body for committing a reviewed discovery proposal.
 */
public record CommitDiscoveryRequest(
        List<CreateProfileRequest> profiles,
        List<String> userBaseDns,
        List<String> groupBaseDns
) {}
