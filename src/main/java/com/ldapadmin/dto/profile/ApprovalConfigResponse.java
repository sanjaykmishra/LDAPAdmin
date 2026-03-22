package com.ldapadmin.dto.profile;

import com.ldapadmin.entity.ProfileApprovalConfig;
import com.ldapadmin.entity.enums.ApproverMode;

import java.util.UUID;

public record ApprovalConfigResponse(
        UUID id,
        UUID profileId,
        boolean requireApproval,
        ApproverMode approverMode,
        String approverGroupDn,
        Integer autoEscalateDays,
        UUID escalationAccountId) {

    public static ApprovalConfigResponse from(ProfileApprovalConfig c) {
        return new ApprovalConfigResponse(
                c.getId(),
                c.getProfile().getId(),
                c.isRequireApproval(),
                c.getApproverMode(),
                c.getApproverGroupDn(),
                c.getAutoEscalateDays(),
                c.getEscalationAccount() != null ? c.getEscalationAccount().getId() : null);
    }
}
