package com.ldapadmin.dto.approval;

import jakarta.validation.constraints.NotBlank;

public record ApprovalRejectRequest(@NotBlank String reason) {
}
