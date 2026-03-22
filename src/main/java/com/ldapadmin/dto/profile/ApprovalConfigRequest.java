package com.ldapadmin.dto.profile;

import com.ldapadmin.entity.enums.ApproverMode;

import java.util.UUID;

public record ApprovalConfigRequest(
        boolean requireApproval,
        ApproverMode approverMode,
        String approverGroupDn,
        Integer autoEscalateDays,
        UUID escalationAccountId) {
}
