package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.approval.PendingApprovalResponse;
import com.ldapadmin.dto.csv.BulkImportRequest;
import com.ldapadmin.dto.ldap.CreateEntryRequest;
import com.ldapadmin.dto.ldap.LdapEntryResponse;
import com.ldapadmin.dto.ldap.MoveUserRequest;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.PendingApproval;
import com.ldapadmin.entity.ProvisioningProfile;
import com.ldapadmin.entity.RegistrationRequest;
import com.ldapadmin.entity.enums.ApprovalRequestType;
import com.ldapadmin.entity.enums.ApprovalStatus;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.RegistrationStatus;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.PendingApprovalRepository;
import com.ldapadmin.repository.RegistrationRequestRepository;
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
    private final AccountRepository accountRepo;
    private final RegistrationRequestRepository registrationRepo;
    private final ProvisioningProfileService profileService;
    private final LdapOperationService ldapOperationService;
    private final AuditService auditService;
    private final ApprovalNotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Lazy to break circular dependency: SelfServiceService → ApprovalWorkflowService → SelfServiceService
    private final ApplicationSettingsService settingsService;
    private final org.springframework.context.ApplicationContext applicationContext;

    private SelfServiceService selfServiceService() {
        return applicationContext.getBean(SelfServiceService.class);
    }

    @Transactional(readOnly = true)
    public boolean isApprovalRequired(UUID profileId) {
        return profileService.isApprovalRequired(profileId);
    }

    /**
     * Finds the profile that contains the given DN for a directory.
     */
    @Transactional(readOnly = true)
    public Optional<ProvisioningProfile> findProfileForDn(UUID directoryId, String dn) {
        return profileService.resolveProfileForDn(directoryId, dn);
    }

    @Transactional
    public PendingApproval submitForApproval(UUID directoryId, UUID profileId,
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
        pa.setProfileId(profileId);
        pa.setRequestedBy(requester.id());
        pa.setStatus(ApprovalStatus.PENDING);
        pa.setRequestType(type);
        pa.setPayload(payloadJson);

        pa = approvalRepo.save(pa);

        auditService.record(requester, directoryId, AuditAction.APPROVAL_SUBMITTED,
                null, Map.of("approvalId", pa.getId().toString(), "requestType", type.name()));

        // Auto-approve if requester is superadmin and bypass setting is enabled
        if (requester.isSuperadmin() && settingsService.getEntity().isSuperadminBypassApproval()) {
            autoApprove(pa, requester);
        } else {
            notificationService.notifyApproversOfNewRequest(pa);
        }

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
                    .filter(pa -> pa.getProfileId() != null
                            && profileService.isApprover(pa.getProfileId(), principal.id()))
                    .toList();
        }

        return visible.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PendingApprovalResponse getApproval(UUID approvalId, AuthPrincipal principal) {
        PendingApproval pa = approvalRepo.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("PendingApproval", approvalId));

        if (!principal.isSuperadmin() && pa.getProfileId() != null
                && !profileService.isApprover(pa.getProfileId(), principal.id())) {
            throw new AccessDeniedException("Not an approver for this profile");
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

        if (approver.id().equals(pa.getRequestedBy())) {
            throw new AccessDeniedException("Cannot approve your own request");
        }

        if (!approver.isSuperadmin() && pa.getProfileId() != null
                && !profileService.isApprover(pa.getProfileId(), approver.id())) {
            throw new AccessDeniedException("Not an approver for this profile");
        }

        // Execute the actual LDAP operation — on failure, store error and keep PENDING
        try {
            if (pa.getRequestType() == ApprovalRequestType.USER_CREATE) {
                executeUserCreate(pa, approver);
            } else if (pa.getRequestType() == ApprovalRequestType.BULK_IMPORT) {
                executeBulkImport(pa, approver);
            } else if (pa.getRequestType() == ApprovalRequestType.USER_MOVE) {
                executeUserMove(pa, approver);
            } else if (pa.getRequestType() == ApprovalRequestType.GROUP_MEMBER_ADD) {
                executeGroupMemberAdd(pa, approver);
            } else if (pa.getRequestType() == ApprovalRequestType.SELF_REGISTRATION) {
                executeSelfRegistration(pa);
            }
        } catch (Exception e) {
            // Provisioning failed — store the error and keep status as PENDING
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            pa.setProvisionError(msg);
            approvalRepo.save(pa);
            log.error("Provisioning failed for approval {}: {}", pa.getId(), msg);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "Provisioning failed: " + msg);
        }

        pa.setProvisionError(null); // clear any previous error on success
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

        if (!approver.isSuperadmin() && pa.getProfileId() != null
                && !profileService.isApprover(pa.getProfileId(), approver.id())) {
            throw new AccessDeniedException("Not an approver for this profile");
        }

        pa.setStatus(ApprovalStatus.REJECTED);
        pa.setRejectReason(reason);
        pa.setReviewedBy(approver.id());
        pa.setReviewedAt(OffsetDateTime.now());
        approvalRepo.save(pa);

        // Update associated registration request if this is a self-registration
        if (pa.getRequestType() == ApprovalRequestType.SELF_REGISTRATION) {
            registrationRepo.findByPendingApprovalId(pa.getId()).ifPresent(regReq -> {
                regReq.setStatus(RegistrationStatus.REJECTED);
                registrationRepo.save(regReq);
            });
        }

        auditService.record(approver, pa.getDirectoryId(), AuditAction.APPROVAL_REJECTED,
                null, Map.of("approvalId", pa.getId().toString(), "reason", reason));

        notificationService.notifyRequesterRejected(pa);

        return toResponse(pa);
    }

    @Transactional
    public PendingApprovalResponse updatePayload(UUID approvalId, AuthPrincipal editor, String newPayload) {
        PendingApproval pa = approvalRepo.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("PendingApproval", approvalId));

        if (pa.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Can only edit requests in PENDING status");
        }

        if (!editor.isSuperadmin() && pa.getProfileId() != null
                && !profileService.isApprover(pa.getProfileId(), editor.id())) {
            throw new AccessDeniedException("Not an approver for this profile");
        }

        pa.setPayload(newPayload);
        pa.setProvisionError(null); // clear error after edit
        approvalRepo.save(pa);

        auditService.record(editor, pa.getDirectoryId(), AuditAction.APPROVAL_REQUEST_EDITED,
                null, Map.of("approvalId", pa.getId().toString()));

        return toResponse(pa);
    }

    @Transactional(readOnly = true)
    public long countPending(UUID directoryId) {
        return approvalRepo.countByDirectoryIdAndStatus(directoryId, ApprovalStatus.PENDING);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Immediately approves and executes a request submitted by a superadmin
     * when the bypass setting is enabled. Creates a full audit trail.
     */
    private void autoApprove(PendingApproval pa, AuthPrincipal requester) {
        if (pa.getRequestType() == ApprovalRequestType.USER_CREATE) {
            executeUserCreate(pa, requester);
        } else if (pa.getRequestType() == ApprovalRequestType.BULK_IMPORT) {
            executeBulkImport(pa, requester);
        } else if (pa.getRequestType() == ApprovalRequestType.USER_MOVE) {
            executeUserMove(pa, requester);
        } else if (pa.getRequestType() == ApprovalRequestType.GROUP_MEMBER_ADD) {
            executeGroupMemberAdd(pa, requester);
        } else if (pa.getRequestType() == ApprovalRequestType.SELF_REGISTRATION) {
            executeSelfRegistration(pa);
        }

        pa.setStatus(ApprovalStatus.APPROVED);
        pa.setReviewedBy(requester.id());
        pa.setReviewedAt(OffsetDateTime.now());
        approvalRepo.save(pa);

        auditService.record(requester, pa.getDirectoryId(), AuditAction.APPROVAL_AUTO_APPROVED,
                null, Map.of("approvalId", pa.getId().toString()));

        log.info("Auto-approved request {} for superadmin {}", pa.getId(), requester.id());
    }

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

    private void executeUserMove(PendingApproval pa, AuthPrincipal approver) {
        try {
            JsonNode root = objectMapper.readTree(pa.getPayload());
            String dn = root.get("dn").asText();
            MoveUserRequest req = objectMapper.treeToValue(root.get("request"), MoveUserRequest.class);
            ldapOperationService.moveUser(pa.getDirectoryId(), approver, dn, req);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize user move payload", e);
        }
    }

    private void executeGroupMemberAdd(PendingApproval pa, AuthPrincipal approver) {
        try {
            JsonNode root = objectMapper.readTree(pa.getPayload());
            String groupDn = root.get("groupDn").asText();
            String memberAttribute = root.get("memberAttribute").asText();
            String memberValue = root.get("memberValue").asText();
            ldapOperationService.addGroupMember(
                    pa.getDirectoryId(), approver, groupDn, memberAttribute, memberValue);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize group member add payload", e);
        }
    }

    private void executeSelfRegistration(PendingApproval pa) {
        RegistrationRequest regReq = registrationRepo.findByPendingApprovalId(pa.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Registration request not found for approval " + pa.getId()));
        selfServiceService().provisionRegisteredUser(regReq);
        regReq.setStatus(RegistrationStatus.APPROVED);
        registrationRepo.save(regReq);
        log.info("Self-registration approved and provisioned for {}", regReq.getEmail());
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
