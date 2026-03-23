package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PermissionService;
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

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private final PendingApprovalRepository approvalRepo;
    private final AccountRepository accountRepo;
    private final RegistrationRequestRepository registrationRepo;
    private final PermissionService permissionService;
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

    /**
     * Checks whether the given operation on a DN requires approval, handling both
     * profiled and unprovisioned OUs. Returns the submitted PendingApproval if
     * approval is required, or empty if the operation can proceed immediately.
     *
     * <p>When no profile matches the target DN, the operation is still submitted
     * for approval (with a null profileId) to prevent the bypass described in
     * finding C2. A warning is logged so operators can configure a profile for
     * the target OU.
     */
    @Transactional
    public Optional<PendingApproval> checkAndSubmitForApproval(
            UUID directoryId, String targetDn, AuthPrincipal requester,
            ApprovalRequestType type, Object payload) {

        Optional<ProvisioningProfile> profile = findProfileForDn(directoryId, targetDn);

        if (profile.isPresent()) {
            if (isApprovalRequired(profile.get().getId())) {
                PendingApproval pa = submitForApproval(
                        directoryId, profile.get().getId(), requester, type, payload);
                return Optional.of(pa);
            }
            return Optional.empty();
        }

        // No profile matches this DN — submit for directory-level approval to
        // prevent the unprovisioned-OU bypass (C2). The approval will have a null
        // profileId, so superadmins or directory-level approvers can review it.
        log.warn("No provisioning profile matches DN [{}] in directory [{}]. "
                + "Submitting for directory-level approval.", targetDn, directoryId);
        PendingApproval pa = submitForApproval(
                directoryId, null, requester, type, payload);
        return Optional.of(pa);
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
                null, buildAuditDetail(pa, null));

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
                    .filter(pa -> canViewApproval(pa, principal))
                    .toList();
        }

        return visible.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PendingApprovalResponse getApproval(UUID approvalId, AuthPrincipal principal) {
        PendingApproval pa = approvalRepo.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("PendingApproval", approvalId));

        if (!principal.isSuperadmin() && !canViewApproval(pa, principal)) {
            throw new AccessDeniedException("Not an approver for this request");
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

        if (!approver.isSuperadmin() && !canViewApproval(pa, approver)) {
            throw new AccessDeniedException("Not an approver for this request");
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
            return toResponse(pa);
        }

        pa.setProvisionError(null); // clear any previous error on success
        pa.setStatus(ApprovalStatus.APPROVED);
        pa.setReviewedBy(approver.id());
        pa.setReviewedAt(OffsetDateTime.now());
        approvalRepo.save(pa);

        auditService.record(approver, pa.getDirectoryId(), AuditAction.APPROVAL_APPROVED,
                null, buildAuditDetail(pa, null));

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

        if (!approver.isSuperadmin() && !canViewApproval(pa, approver)) {
            throw new AccessDeniedException("Not an approver for this request");
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
                null, buildAuditDetail(pa, Map.of("reason", reason)));

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

        // Block the original requester from editing their own pending request
        if (editor.id().equals(pa.getRequestedBy())) {
            throw new AccessDeniedException("Cannot edit your own approval request");
        }

        if (!editor.isSuperadmin() && !canViewApproval(pa, editor)) {
            throw new AccessDeniedException("Not an approver for this request");
        }

        // Validate the new payload matches the original request type's schema
        validatePayloadSchema(pa.getRequestType(), newPayload);

        String oldPayload = pa.getPayload();
        pa.setPayload(newPayload);
        pa.setProvisionError(null); // clear error after edit
        approvalRepo.save(pa);

        // Log the payload diff in the audit trail
        Map<String, Object> diffDetail = new LinkedHashMap<>();
        diffDetail.put("previousPayload", obfuscatePasswords(oldPayload));
        diffDetail.put("updatedPayload", obfuscatePasswords(newPayload));
        auditService.record(editor, pa.getDirectoryId(), AuditAction.APPROVAL_REQUEST_EDITED,
                null, buildAuditDetail(pa, diffDetail));

        return toResponse(pa);
    }

    /**
     * Determines whether a non-superadmin principal can view/act on a pending approval.
     * For profile-scoped approvals, checks if the principal is a designated approver.
     * For directory-level approvals (null profileId, e.g. bulk imports targeting
     * unprovisioned OUs), falls back to checking directory access.
     */
    private boolean canViewApproval(PendingApproval pa, AuthPrincipal principal) {
        if (pa.getProfileId() != null) {
            return profileService.isApprover(pa.getProfileId(), principal.id());
        }
        // Directory-level approval — any admin with access to this directory can review
        return permissionService.getAuthorizedDirectoryIds(principal)
                .contains(pa.getDirectoryId());
    }

    /**
     * Validates that the new payload can be deserialized according to the
     * expected schema for the given request type. Rejects payloads that
     * don't match the original request structure.
     */
    private void validatePayloadSchema(ApprovalRequestType requestType, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || root.isMissingNode()) {
                throw new IllegalArgumentException("Payload must be valid JSON");
            }
            switch (requestType) {
                case USER_CREATE -> objectMapper.treeToValue(root, CreateEntryRequest.class);
                case BULK_IMPORT -> {
                    if (!root.has("request") || !root.has("csvContent")) {
                        throw new IllegalArgumentException(
                                "BULK_IMPORT payload must contain 'request' and 'csvContent' fields");
                    }
                    objectMapper.treeToValue(root.get("request"), BulkImportRequest.class);
                }
                case USER_MOVE -> {
                    if (!root.has("dn") || !root.has("request")) {
                        throw new IllegalArgumentException(
                                "USER_MOVE payload must contain 'dn' and 'request' fields");
                    }
                    objectMapper.treeToValue(root.get("request"), MoveUserRequest.class);
                }
                case GROUP_MEMBER_ADD -> {
                    for (String field : List.of("groupDn", "memberAttribute", "memberValue")) {
                        if (!root.has(field)) {
                            throw new IllegalArgumentException(
                                    "GROUP_MEMBER_ADD payload must contain '" + field + "' field");
                        }
                    }
                }
                case SELF_REGISTRATION -> { /* no structural validation needed */ }
                case PLAYBOOK_EXECUTE -> { /* no structural validation needed */ }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload does not match expected schema for "
                    + requestType + ": " + e.getMessage(), e);
        }
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
                null, buildAuditDetail(pa, null));

        log.info("Auto-approved request {} for superadmin {}", pa.getId(), requester.id());
    }

    private void executeUserCreate(PendingApproval pa, AuthPrincipal approver) {
        try {
            CreateEntryRequest req = objectMapper.readValue(pa.getPayload(), CreateEntryRequest.class);
            // Re-apply computed/default/fixed attributes in case the payload was edited
            if (pa.getProfileId() != null) {
                Map<String, List<String>> attrs = new LinkedHashMap<>(req.attributes());
                profileService.applyDefaults(pa.getProfileId(), attrs);
                req = new CreateEntryRequest(req.dn(), attrs);
            }
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

    private static final Set<String> PASSWORD_ATTRIBUTES = Set.of(
            "userpassword", "unicodepwd", "password");

    /**
     * Builds the detail map for approval-related audit events, including the
     * request type and payload. Password attributes in the payload are
     * obfuscated before the detail is written.
     */
    private Map<String, Object> buildAuditDetail(PendingApproval pa, Map<String, Object> extra) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("approvalId", pa.getId().toString());
        detail.put("requestType", pa.getRequestType().name());
        detail.put("payload", obfuscatePasswords(pa.getPayload()));
        if (extra != null) detail.putAll(extra);
        return detail;
    }

    /**
     * Returns a copy of the JSON payload with any password attribute values
     * replaced by "********".
     */
    private String obfuscatePasswords(String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            obfuscateNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            return "***unreadable***";
        }
    }

    private void obfuscateNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> fieldNames = obj.fieldNames();
            List<String> names = new ArrayList<>();
            fieldNames.forEachRemaining(names::add);
            for (String name : names) {
                if (PASSWORD_ATTRIBUTES.contains(name.toLowerCase())) {
                    obj.put(name, "********");
                } else {
                    obfuscateNode(obj.get(name));
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                obfuscateNode(child);
            }
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
