package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.dto.approval.ApprovalRejectRequest;
import com.ldapadmin.dto.approval.PendingApprovalResponse;
import com.ldapadmin.service.ApprovalWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directories/{directoryId}/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalWorkflowService service;

    @GetMapping
    public List<PendingApprovalResponse> list(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.listPending(directoryId, principal);
    }

    @GetMapping("/{approvalId}")
    public PendingApprovalResponse get(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID approvalId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getApproval(approvalId, principal);
    }

    @PostMapping("/{approvalId}/approve")
    public PendingApprovalResponse approve(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID approvalId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.approve(approvalId, principal);
    }

    @PostMapping("/{approvalId}/reject")
    public PendingApprovalResponse reject(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID approvalId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ApprovalRejectRequest req) {
        return service.reject(approvalId, principal, req.reason());
    }

    @GetMapping("/count")
    public Map<String, Long> countPending(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("pending", service.countPending(directoryId));
    }
}
