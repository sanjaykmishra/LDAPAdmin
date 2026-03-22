package com.ldapadmin.service;

import com.ldapadmin.dto.profile.*;
import com.ldapadmin.dto.profile.CreateProfileRequest.AttributeConfigEntry;
import com.ldapadmin.dto.profile.CreateProfileRequest.GroupAssignmentEntry;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.ApproverMode;
import com.ldapadmin.entity.enums.ExpiryAction;
import com.ldapadmin.entity.enums.InputType;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core CRUD and provisioning logic for provisioning profiles.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProvisioningProfileService {

    private final ProvisioningProfileRepository      profileRepo;
    private final ProfileAttributeConfigRepository   attrConfigRepo;
    private final ProfileGroupAssignmentRepository   groupAssignmentRepo;
    private final ProfileLifecyclePolicyRepository   lifecycleRepo;
    private final ProfileApprovalConfigRepository    approvalConfigRepo;
    private final ProfileApproverRepository          approverRepo;
    private final DirectoryConnectionRepository      dirRepo;
    private final AccountRepository                  accountRepo;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static final Pattern EXPRESSION_VAR = Pattern.compile("\\$\\{(\\w+)}");

    // ── Profile CRUD ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProfileResponse> list(UUID directoryId) {
        requireDirectory(directoryId);
        return profileRepo.findAllByDirectoryIdOrderByNameAsc(directoryId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> listAll() {
        return profileRepo.findAllByOrderByDirectoryIdAscNameAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID profileId) {
        return toResponse(requireProfile(profileId));
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID directoryId, UUID profileId) {
        return toResponse(requireProfileInDirectory(directoryId, profileId));
    }

    @Transactional
    public ProfileResponse create(UUID directoryId, CreateProfileRequest req) {
        DirectoryConnection dir = requireDirectory(directoryId);

        if (profileRepo.existsByDirectoryIdAndName(directoryId, req.name())) {
            throw new ConflictException(
                    "Profile [" + req.name() + "] already exists in this directory");
        }

        ProvisioningProfile profile = new ProvisioningProfile();
        profile.setDirectory(dir);
        applyCommonFields(profile, req.name(), req.description(), req.targetOuDn(),
                req.objectClassNames(), req.rdnAttribute(), req.showDnField(),
                req.enabled(), req.selfRegistrationAllowed());
        profile = profileRepo.save(profile);

        saveAttributeConfigs(profile, req.attributeConfigs());
        saveGroupAssignments(profile, req.groupAssignments());

        return toResponse(profile);
    }

    @Transactional
    public ProfileResponse update(UUID directoryId, UUID profileId, UpdateProfileRequest req) {
        ProvisioningProfile profile = requireProfileInDirectory(directoryId, profileId);

        // Check name uniqueness if changed
        if (!profile.getName().equals(req.name()) &&
                profileRepo.existsByDirectoryIdAndName(profile.getDirectory().getId(), req.name())) {
            throw new ConflictException(
                    "Profile [" + req.name() + "] already exists in this directory");
        }

        applyCommonFields(profile, req.name(), req.description(), req.targetOuDn(),
                req.objectClassNames(), req.rdnAttribute(), req.showDnField(),
                req.enabled(), req.selfRegistrationAllowed());
        profile = profileRepo.save(profile);

        // Replace attribute configs
        attrConfigRepo.deleteAllByProfileId(profileId);
        attrConfigRepo.flush();
        saveAttributeConfigs(profile, req.attributeConfigs());

        // Replace group assignments
        groupAssignmentRepo.deleteAllByProfileId(profileId);
        saveGroupAssignments(profile, req.groupAssignments());

        return toResponse(profile);
    }

    @Transactional
    public void delete(UUID directoryId, UUID profileId) {
        ProvisioningProfile profile = requireProfileInDirectory(directoryId, profileId);
        profileRepo.delete(profile);
    }

    @Transactional
    public ProfileResponse clone(UUID directoryId, UUID profileId, String newName) {
        ProvisioningProfile source = requireProfileInDirectory(directoryId, profileId);

        if (profileRepo.existsByDirectoryIdAndName(source.getDirectory().getId(), newName)) {
            throw new ConflictException(
                    "Profile [" + newName + "] already exists in this directory");
        }

        ProvisioningProfile copy = new ProvisioningProfile();
        copy.setDirectory(source.getDirectory());
        copy.setName(newName);
        copy.setDescription(source.getDescription());
        copy.setTargetOuDn(source.getTargetOuDn());
        copy.setObjectClassNames(new ArrayList<>(source.getObjectClassNames()));
        copy.setRdnAttribute(source.getRdnAttribute());
        copy.setShowDnField(source.isShowDnField());
        copy.setEnabled(false); // clones start disabled
        copy.setSelfRegistrationAllowed(false);
        copy = profileRepo.save(copy);

        // Clone attribute configs
        List<ProfileAttributeConfig> sourceConfigs =
                attrConfigRepo.findAllByProfileIdOrderByDisplayOrderAsc(profileId);
        for (ProfileAttributeConfig sc : sourceConfigs) {
            ProfileAttributeConfig cc = new ProfileAttributeConfig();
            cc.setProfile(copy);
            cc.setAttributeName(sc.getAttributeName());
            cc.setCustomLabel(sc.getCustomLabel());
            cc.setInputType(sc.getInputType());
            cc.setRequiredOnCreate(sc.isRequiredOnCreate());
            cc.setEditableOnCreate(sc.isEditableOnCreate());
            cc.setEditableOnUpdate(sc.isEditableOnUpdate());
            cc.setSelfServiceEdit(sc.isSelfServiceEdit());
            cc.setDefaultValue(sc.getDefaultValue());
            cc.setComputedExpression(sc.getComputedExpression());
            cc.setValidationRegex(sc.getValidationRegex());
            cc.setValidationMessage(sc.getValidationMessage());
            cc.setAllowedValues(sc.getAllowedValues());
            cc.setMinLength(sc.getMinLength());
            cc.setMaxLength(sc.getMaxLength());
            cc.setSectionName(sc.getSectionName());
            cc.setColumnSpan(sc.getColumnSpan());
            cc.setDisplayOrder(sc.getDisplayOrder());
            cc.setHidden(sc.isHidden());
            attrConfigRepo.save(cc);
        }

        // Clone group assignments
        List<ProfileGroupAssignment> sourceGroups =
                groupAssignmentRepo.findAllByProfileIdOrderByDisplayOrderAsc(profileId);
        for (ProfileGroupAssignment sg : sourceGroups) {
            ProfileGroupAssignment cg = new ProfileGroupAssignment();
            cg.setProfile(copy);
            cg.setGroupDn(sg.getGroupDn());
            cg.setMemberAttribute(sg.getMemberAttribute());
            cg.setDisplayOrder(sg.getDisplayOrder());
            groupAssignmentRepo.save(cg);
        }

        return toResponse(copy);
    }

    // ── Lifecycle Policy ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LifecyclePolicyResponse getLifecyclePolicy(UUID profileId) {
        requireProfile(profileId);
        ProfileLifecyclePolicy policy = lifecycleRepo.findByProfileId(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No lifecycle policy for profile " + profileId));
        return LifecyclePolicyResponse.from(policy);
    }

    @Transactional
    public LifecyclePolicyResponse setLifecyclePolicy(UUID profileId, LifecyclePolicyRequest req) {
        ProvisioningProfile profile = requireProfile(profileId);
        ProfileLifecyclePolicy policy = lifecycleRepo.findByProfileId(profileId)
                .orElseGet(() -> {
                    ProfileLifecyclePolicy p = new ProfileLifecyclePolicy();
                    p.setProfile(profile);
                    return p;
                });

        policy.setExpiresAfterDays(req.expiresAfterDays());
        policy.setMaxRenewals(req.maxRenewals());
        policy.setRenewalDays(req.renewalDays());
        policy.setOnExpiryAction(req.onExpiryAction() != null ? req.onExpiryAction() : ExpiryAction.DISABLE);
        policy.setOnExpiryMoveDn(req.onExpiryMoveDn());
        policy.setOnExpiryRemoveGroups(req.onExpiryRemoveGroups());
        policy.setOnExpiryNotify(req.onExpiryNotify());
        policy.setWarningDaysBefore(req.warningDaysBefore());

        return LifecyclePolicyResponse.from(lifecycleRepo.save(policy));
    }

    @Transactional
    public void deleteLifecyclePolicy(UUID profileId) {
        requireProfile(profileId);
        lifecycleRepo.deleteByProfileId(profileId);
    }

    // ── Approval Config ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApprovalConfigResponse getApprovalConfig(UUID profileId) {
        requireProfile(profileId);
        ProfileApprovalConfig config = approvalConfigRepo.findByProfileId(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No approval config for profile " + profileId));
        return ApprovalConfigResponse.from(config);
    }

    @Transactional
    public ApprovalConfigResponse setApprovalConfig(UUID profileId, ApprovalConfigRequest req) {
        ProvisioningProfile profile = requireProfile(profileId);
        ProfileApprovalConfig config = approvalConfigRepo.findByProfileId(profileId)
                .orElseGet(() -> {
                    ProfileApprovalConfig c = new ProfileApprovalConfig();
                    c.setProfile(profile);
                    return c;
                });

        config.setRequireApproval(req.requireApproval());
        config.setApproverMode(req.approverMode() != null ? req.approverMode() : ApproverMode.DATABASE);
        config.setApproverGroupDn(req.approverGroupDn());
        config.setAutoEscalateDays(req.autoEscalateDays());
        if (req.escalationAccountId() != null) {
            config.setEscalationAccount(accountRepo.findById(req.escalationAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", req.escalationAccountId())));
        } else {
            config.setEscalationAccount(null);
        }

        return ApprovalConfigResponse.from(approvalConfigRepo.save(config));
    }

    // ── Approvers ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProfileApproverResponse> getApprovers(UUID profileId) {
        requireProfile(profileId);
        return approverRepo.findAllByProfileIdWithAccount(profileId).stream()
                .map(pa -> new ProfileApproverResponse(
                        pa.getAdminAccount().getId(),
                        pa.getAdminAccount().getUsername(),
                        pa.getAdminAccount().getEmail()))
                .toList();
    }

    @Transactional
    public List<ProfileApproverResponse> setApprovers(UUID profileId, List<UUID> accountIds) {
        ProvisioningProfile profile = requireProfile(profileId);
        approverRepo.deleteAllByProfileId(profileId);

        for (UUID accountId : accountIds) {
            Account account = accountRepo.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
            ProfileApprover pa = new ProfileApprover();
            pa.setProfile(profile);
            pa.setAdminAccount(account);
            approverRepo.save(pa);
        }

        return approverRepo.findAllByProfileIdWithAccount(profileId).stream()
                .map(pa -> new ProfileApproverResponse(
                        pa.getAdminAccount().getId(),
                        pa.getAdminAccount().getUsername(),
                        pa.getAdminAccount().getEmail()))
                .toList();
    }

    // ── Profile resolution ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<ProvisioningProfile> resolveProfileForDn(UUID directoryId, String dn) {
        List<ProvisioningProfile> profiles =
                profileRepo.findAllByDirectoryIdAndEnabledTrue(directoryId);
        String dnLower = dn.toLowerCase();

        // Find profiles whose target OU is a suffix of the DN; most specific match wins
        return profiles.stream()
                .filter(p -> dnLower.endsWith(p.getTargetOuDn().toLowerCase()))
                .max(Comparator.comparingInt(p -> p.getTargetOuDn().length()));
    }

    @Transactional(readOnly = true)
    public boolean isApprovalRequired(UUID profileId) {
        return approvalConfigRepo.findByProfileId(profileId)
                .map(ProfileApprovalConfig::isRequireApproval)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isApprover(UUID profileId, UUID accountId) {
        return approverRepo.existsByProfileIdAndAdminAccountId(profileId, accountId);
    }

    // ── Provisioning helpers ──────────────────────────────────────────────────

    /**
     * Evaluates computed expressions by interpolating ${attributeName} references.
     */
    public String evaluateExpression(String expression, Map<String, List<String>> attributes) {
        if (expression == null || expression.isBlank()) return null;

        Matcher matcher = EXPRESSION_VAR.matcher(expression);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String attrName = matcher.group(1);
            List<String> values = attributes.get(attrName);
            String replacement = (values != null && !values.isEmpty()) ? values.get(0) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Validates attribute values against profile attribute configs.
     */
    @Transactional(readOnly = true)
    public void validateAttributes(UUID profileId, Map<String, List<String>> attributes) {
        List<ProfileAttributeConfig> configs =
                attrConfigRepo.findAllByProfileIdOrderByDisplayOrderAsc(profileId);

        for (ProfileAttributeConfig config : configs) {
            List<String> values = attributes.get(config.getAttributeName());
            String value = (values != null && !values.isEmpty()) ? values.get(0) : null;

            // Required check
            if (config.isRequiredOnCreate() && (value == null || value.isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Attribute [" + config.getAttributeName() + "] is required");
            }

            if (value == null || value.isBlank()) continue;

            // Length checks
            if (config.getMinLength() != null && value.length() < config.getMinLength()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Attribute [" + config.getAttributeName() + "] must be at least "
                                + config.getMinLength() + " characters");
            }
            if (config.getMaxLength() != null && value.length() > config.getMaxLength()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Attribute [" + config.getAttributeName() + "] must be at most "
                                + config.getMaxLength() + " characters");
            }

            // Regex check
            if (config.getValidationRegex() != null && !config.getValidationRegex().isBlank()) {
                if (!value.matches(config.getValidationRegex())) {
                    String msg = config.getValidationMessage() != null
                            ? config.getValidationMessage()
                            : "Attribute [" + config.getAttributeName() + "] does not match the required format";
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
                }
            }

            // Allowed values check
            if (config.getAllowedValues() != null && !config.getAllowedValues().isBlank()) {
                try {
                    List<String> allowed = objectMapper.readValue(config.getAllowedValues(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    if (!allowed.contains(value)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Attribute [" + config.getAttributeName()
                                        + "] value is not in the allowed values list");
                    }
                } catch (ResponseStatusException rse) {
                    throw rse;
                } catch (Exception e) {
                    log.warn("Failed to parse allowed values JSON for attribute [{}]: {}",
                            config.getAttributeName(), e.getMessage());
                }
            }
        }
    }

    /**
     * Applies defaults, computed expressions, and fixed values to the attribute map.
     */
    @Transactional(readOnly = true)
    public void applyDefaults(UUID profileId, Map<String, List<String>> attributes) {
        List<ProfileAttributeConfig> configs =
                attrConfigRepo.findAllByProfileIdOrderByDisplayOrderAsc(profileId);

        for (ProfileAttributeConfig config : configs) {
            List<String> values = attributes.get(config.getAttributeName());
            boolean hasValue = values != null && !values.isEmpty()
                    && values.get(0) != null && !values.get(0).isBlank();

            // Apply HIDDEN_FIXED values always
            if (config.getInputType() == InputType.HIDDEN_FIXED && config.getDefaultValue() != null) {
                attributes.put(config.getAttributeName(), List.of(config.getDefaultValue()));
                continue;
            }

            // Apply computed expressions
            if (!hasValue && config.getComputedExpression() != null
                    && !config.getComputedExpression().isBlank()) {
                String computed = evaluateExpression(config.getComputedExpression(), attributes);
                if (computed != null && !computed.isBlank()) {
                    attributes.put(config.getAttributeName(), List.of(computed));
                    continue;
                }
            }

            // Apply static default
            if (!hasValue && config.getDefaultValue() != null && !config.getDefaultValue().isBlank()) {
                attributes.put(config.getAttributeName(), List.of(config.getDefaultValue()));
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ProfileResponse toResponse(ProvisioningProfile profile) {
        List<ProfileAttributeConfig> configs =
                attrConfigRepo.findAllByProfileIdOrderByDisplayOrderAsc(profile.getId());
        List<ProfileGroupAssignment> groups =
                groupAssignmentRepo.findAllByProfileIdOrderByDisplayOrderAsc(profile.getId());
        return ProfileResponse.from(profile, configs, groups);
    }

    private ProvisioningProfile requireProfile(UUID profileId) {
        return profileRepo.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("ProvisioningProfile", profileId));
    }

    private ProvisioningProfile requireProfileInDirectory(UUID directoryId, UUID profileId) {
        return profileRepo.findByIdAndDirectoryId(profileId, directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("ProvisioningProfile", profileId));
    }

    private DirectoryConnection requireDirectory(UUID directoryId) {
        return dirRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
    }

    private void applyCommonFields(ProvisioningProfile profile, String name, String description,
                                    String targetOuDn, List<String> objectClassNames,
                                    String rdnAttribute, boolean showDnField,
                                    boolean enabled, boolean selfRegistrationAllowed) {
        profile.setName(name);
        profile.setDescription(description);
        profile.setTargetOuDn(targetOuDn);
        profile.setObjectClassNames(new ArrayList<>(objectClassNames));
        profile.setRdnAttribute(rdnAttribute);
        profile.setShowDnField(showDnField);
        profile.setEnabled(enabled);
        profile.setSelfRegistrationAllowed(selfRegistrationAllowed);
    }

    private void saveAttributeConfigs(ProvisioningProfile profile,
                                       List<AttributeConfigEntry> entries) {
        if (entries == null || entries.isEmpty()) return;

        for (int i = 0; i < entries.size(); i++) {
            AttributeConfigEntry e = entries.get(i);
            ProfileAttributeConfig c = new ProfileAttributeConfig();
            c.setProfile(profile);
            c.setAttributeName(e.attributeName());
            c.setCustomLabel(e.customLabel());
            c.setInputType(InputType.valueOf(e.inputType()));
            c.setRequiredOnCreate(e.requiredOnCreate());
            c.setEditableOnCreate(e.editableOnCreate());
            c.setEditableOnUpdate(e.editableOnUpdate());
            c.setSelfServiceEdit(e.selfServiceEdit());
            c.setSelfRegistrationEdit(e.selfRegistrationEdit());
            c.setDefaultValue(e.defaultValue());
            c.setComputedExpression(e.computedExpression());
            c.setValidationRegex(e.validationRegex());
            c.setValidationMessage(e.validationMessage());
            c.setAllowedValues(e.allowedValues());
            c.setMinLength(e.minLength());
            c.setMaxLength(e.maxLength());
            c.setSectionName(e.sectionName());
            c.setColumnSpan(e.columnSpan() != null ? e.columnSpan() : 3);
            c.setDisplayOrder(i);
            c.setHidden(e.hidden());
            c.setRegistrationSectionName(e.registrationSectionName());
            c.setRegistrationColumnSpan(e.registrationColumnSpan() != null ? e.registrationColumnSpan() : 3);
            c.setRegistrationDisplayOrder(i);
            c.setSelfServiceSectionName(e.selfServiceSectionName());
            c.setSelfServiceColumnSpan(e.selfServiceColumnSpan() != null ? e.selfServiceColumnSpan() : 3);
            c.setSelfServiceDisplayOrder(i);
            attrConfigRepo.save(c);
        }
    }

    private void saveGroupAssignments(ProvisioningProfile profile,
                                       List<GroupAssignmentEntry> entries) {
        if (entries == null || entries.isEmpty()) return;

        for (int i = 0; i < entries.size(); i++) {
            GroupAssignmentEntry e = entries.get(i);
            ProfileGroupAssignment g = new ProfileGroupAssignment();
            g.setProfile(profile);
            g.setGroupDn(e.groupDn());
            g.setMemberAttribute(e.memberAttribute() != null ? e.memberAttribute() : "member");
            g.setDisplayOrder(i);
            groupAssignmentRepo.save(g);
        }
    }
}
