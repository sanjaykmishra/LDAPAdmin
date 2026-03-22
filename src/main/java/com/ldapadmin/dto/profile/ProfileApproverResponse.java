package com.ldapadmin.dto.profile;

import java.util.UUID;

public record ProfileApproverResponse(
        UUID accountId,
        String username,
        String email) {
}
