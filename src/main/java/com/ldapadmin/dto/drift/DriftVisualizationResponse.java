package com.ldapadmin.dto.drift;

import java.util.List;

public record DriftVisualizationResponse(
        List<PeerGroupViz> peerGroups
) {
    public record PeerGroupViz(
            String name,
            int userCount,
            List<GroupMembership> groups,
            List<Outlier> outliers
    ) {}

    public record GroupMembership(
            String groupName,
            double membershipPct
    ) {}

    public record Outlier(
            String userDn,
            String displayName,
            List<String> extraGroups,
            String severity
    ) {}
}
