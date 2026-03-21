package com.ldapadmin.dto.approval;

import com.ldapadmin.entity.PendingApproval;
import com.ldapadmin.entity.enums.ApprovalRequestType;
import com.ldapadmin.entity.enums.ApprovalStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PendingApprovalResponse(
        UUID id,
        UUID directoryId,
        UUID realmId,
        UUID requestedBy,
        String requesterUsername,
        ApprovalStatus status,
        ApprovalRequestType requestType,
        String payload,
        String rejectReason,
        UUID reviewedBy,
        String reviewerUsername,
        OffsetDateTime createdAt,
        OffsetDateTime reviewedAt) {

    public static PendingApprovalResponse from(PendingApproval pa,
                                                String requesterUsername,
                                                String reviewerUsername) {
        return new PendingApprovalResponse(
                pa.getId(),
                pa.getDirectoryId(),
                pa.getRealmId(),
                pa.getRequestedBy(),
                requesterUsername,
                pa.getStatus(),
                pa.getRequestType(),
                pa.getPayload(),
                pa.getRejectReason(),
                pa.getReviewedBy(),
                reviewerUsername,
                pa.getCreatedAt(),
                pa.getReviewedAt());
    }
}
