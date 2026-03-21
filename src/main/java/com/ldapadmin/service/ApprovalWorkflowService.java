package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.approval.PendingApprovalResponse;
import com.ldapadmin.dto.csv.BulkImportRequest;
import com.ldapadmin.dto.ldap.CreateEntryRequest;
import com.ldapadmin.dto.ldap.LdapEntryResponse;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.PendingApproval;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.enums.ApprovalRequestType;
import com.ldapadmin.entity.enums.ApprovalStatus;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.PendingApprovalRepository;
import com.ldapadmin.repository.RealmRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private final PendingApprovalRepository approvalRepo;
    private final RealmRepository realmRepo;
    private final AccountRepository accountRepo;
    private final RealmSettingService realmSettingService;
    private final RealmApproverService realmApproverService;
    private final LdapOperationService ldapOperationService;
    private final AuditService auditService;
    private final ApprovalNotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public boolean isApprovalRequired(UUID realmId) {
        return realmSettingService.isApprovalRequired(realmId);
    }

    /**
     * Finds the realm that contains the given DN for a directory.
     * Matches by checking if the DN ends with the realm's userBaseDn.
     */
    @Transactional(readOnly = true)
    public Optional<Realm> findRealmForDn(UUID directoryId, String dn) {
        List<Realm> realms = realmRepo.findAllByDirectoryIdOrderByNameAsc(directoryId);
        String dnLower = dn.toLowerCase();
        return realms.stream()
                .filter(r -> dnLower.endsWith(r.getUserBaseDn().toLowerCase()))
                .findFirst();
    }

    @Transactional
    public PendingApproval submitForApproval(UUID directoryId, UUID realmId,
                                              AuthPrincipal requester,
                                              ApprovalRequestType type,
                                              Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize approval payload", e);
        }

        PendingApproval pa = new PendingApproval();
        pa.setDirectoryId(directoryId);
        pa.setRealmId(realmId);
        pa.setRequestedBy(requester.id());
        pa.setStatus(ApprovalStatus.PENDING);
        pa.setRequestType(type);
        pa.setPayload(payloadJson);

        pa = approvalRepo.save(pa);

        auditService.record(requester, directoryId, AuditAction.APPROVAL_SUBMITTED,
                null, Map.of("approvalId", pa.getId().toString(), "requestType", type.name()));

        notificationService.notifyApproversOfNewRequest(pa);

        return pa;
    }

    @Transactional(readOnly = true)
    public List<PendingApprovalResponse> listPending(UUID directoryId, AuthPrincipal principal) {
        List<PendingApproval> all = approvalRepo.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId);

        // Filter to only approvals where the caller is an approver (or superadmin)
        List<PendingApproval> visible;
        if (principal.isSuperadmin()) {
            visible = all;
        } else {
            visible = all.stream()
                    .filter(pa -> realmApproverService.isApprover(pa.getRealmId(), principal.id()))
                    .toList();
        }

        return visible.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PendingApprovalResponse getApproval(UUID approvalId, AuthPrincipal principal) {
        PendingApproval pa = approvalRepo.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("PendingApproval", approvalId));

        if (!principal.isSuperadmin() && !realmApproverService.isApprover(pa.getRealmId(), principal.id())) {
            throw new AccessDeniedException("Not an approver for this realm");
        }

        return toResponse(pa);
    }

    @Transactional
    public PendingApprovalResponse approve(UUID approvalId, AuthPrincipal approver) {
        PendingApproval pa = approvalRepo.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("PendingApproval", approvalId));

        if (pa.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval is not in PENDING status");
        }

        if (!approver.isSuperadmin() && !realmApproverService.isApprover(pa.getRealmId(), approver.id())) {
            throw new AccessDeniedException("Not an approver for this realm");
        }

        // Execute the actual LDAP operation
        if (pa.getRequestType() == ApprovalRequestType.USER_CREATE) {
            executeUserCreate(pa, approver);
        } else if (pa.getRequestType() == ApprovalRequestType.BULK_IMPORT) {
            executeBulkImport(pa, approver);
        }

        pa.setStatus(ApprovalStatus.APPROVED);
        pa.setReviewedBy(approver.id());
        pa.setReviewedAt(OffsetDateTime.now());
        approvalRepo.save(pa);

        auditService.record(approver, pa.getDirectoryId(), AuditAction.APPROVAL_APPROVED,
                null, Map.of("approvalId", pa.getId().toString()));

        notificationService.notifyRequesterApproved(pa);

        return toResponse(pa);
    }

    @Transactional
    public PendingApprovalResponse reject(UUID approvalId, AuthPrincipal approver, String reason) {
        PendingApproval pa = approvalRepo.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("PendingApproval", approvalId));

        if (pa.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval is not in PENDING status");
        }

        if (!approver.isSuperadmin() && !realmApproverService.isApprover(pa.getRealmId(), approver.id())) {
            throw new AccessDeniedException("Not an approver for this realm");
        }

        pa.setStatus(ApprovalStatus.REJECTED);
        pa.setRejectReason(reason);
        pa.setReviewedBy(approver.id());
        pa.setReviewedAt(OffsetDateTime.now());
        approvalRepo.save(pa);

        auditService.record(approver, pa.getDirectoryId(), AuditAction.APPROVAL_REJECTED,
                null, Map.of("approvalId", pa.getId().toString(), "reason", reason));

        notificationService.notifyRequesterRejected(pa);

        return toResponse(pa);
    }

    @Transactional(readOnly = true)
    public long countPending(UUID directoryId) {
        return approvalRepo.countByDirectoryIdAndStatus(directoryId, ApprovalStatus.PENDING);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void executeUserCreate(PendingApproval pa, AuthPrincipal approver) {
        try {
            CreateEntryRequest req = objectMapper.readValue(pa.getPayload(), CreateEntryRequest.class);
            ldapOperationService.createUser(pa.getDirectoryId(), approver, req);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize user creation payload", e);
        }
    }

    private void executeBulkImport(PendingApproval pa, AuthPrincipal approver) {
        try {
            JsonNode root = objectMapper.readTree(pa.getPayload());
            BulkImportRequest req = objectMapper.treeToValue(root.get("request"), BulkImportRequest.class);
            String csvBase64 = root.get("csvContent").asText();
            byte[] csvBytes = java.util.Base64.getDecoder().decode(csvBase64);
            ldapOperationService.bulkImportUsers(
                    pa.getDirectoryId(), approver,
                    new java.io.ByteArrayInputStream(csvBytes), req);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize bulk import payload", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to execute bulk import", e);
        }
    }

    private PendingApprovalResponse toResponse(PendingApproval pa) {
        String requesterUsername = accountRepo.findById(pa.getRequestedBy())
                .map(Account::getUsername).orElse(null);
        String reviewerUsername = pa.getReviewedBy() != null
                ? accountRepo.findById(pa.getReviewedBy()).map(Account::getUsername).orElse(null)
                : null;
        return PendingApprovalResponse.from(pa, requesterUsername, reviewerUsername);
    }
}
