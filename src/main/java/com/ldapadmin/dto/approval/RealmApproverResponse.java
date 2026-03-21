package com.ldapadmin.dto.approval;

import java.util.UUID;

public record RealmApproverResponse(UUID accountId, String username, String email) {
}
