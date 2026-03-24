package com.ldapadmin.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * JSONB-mapped configuration stored inside a {@link CampaignTemplate}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignTemplateConfig implements Serializable {

    private Integer deadlineDays;
    private Integer recurrenceMonths;
    private boolean autoRevoke;
    private boolean autoRevokeOnExpiry;
    private List<GroupConfig> groups;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupConfig implements Serializable {
        private String groupDn;
        private String memberAttribute;
        private UUID reviewerAccountId;
    }
}
