package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.profile.AttributeProfileDto;
import com.ldapadmin.dto.profile.AttributeProfileEntryDto;
import com.ldapadmin.dto.profile.CreateAttributeProfileRequest;
import com.ldapadmin.dto.profile.UpsertAttributeProfileEntryRequest;
import com.ldapadmin.entity.AttributeProfile;
import com.ldapadmin.entity.AttributeProfileEntry;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.InputType;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AttributeProfileEntryRepository;
import com.ldapadmin.repository.AttributeProfileRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD operations for attribute profiles and their per-attribute entries (§5.2).
 *
 * <h3>Isolation model</h3>
 * <ul>
 *   <li>Non-superadmin principals can only access directories belonging to their
 *       own tenant.</li>
 *   <li>Superadmins may access any directory.</li>
 * </ul>
 *
 * <h3>Default profile</h3>
 * <p>Each directory may have at most one default profile ({@code branchDn = "*"},
 * {@code isDefault = true}).  The database enforces this with a partial unique
 * index; the service layer also validates it to produce a friendly error message
 * before hitting the constraint.</p>
 */
@Service
@RequiredArgsConstructor
public class AttributeProfileService {

    private final AttributeProfileRepository      profileRepo;
    private final AttributeProfileEntryRepository entryRepo;
    private final DirectoryConnectionRepository   dirRepo;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttributeProfileDto> listByDirectory(UUID directoryId,
                                                      AuthPrincipal principal) {
        loadDirectory(directoryId, principal);
        return profileRepo.findAllByDirectoryId(directoryId)
                .stream()
                .map(p -> toDto(p, entryRepo.findAllByProfileIdOrderByDisplayOrderAsc(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AttributeProfileDto getById(UUID directoryId, UUID profileId,
                                        AuthPrincipal principal) {
        loadDirectory(directoryId, principal);
        AttributeProfile profile = findProfile(profileId, directoryId, principal);
        return toDto(profile, entryRepo.findAllByProfileIdOrderByDisplayOrderAsc(profileId));
    }

    @Transactional
    public AttributeProfileDto create(UUID directoryId,
                                       CreateAttributeProfileRequest req,
                                       AuthPrincipal principal) {
        DirectoryConnection dir = loadDirectory(directoryId, principal);

        if (profileRepo.existsByDirectoryIdAndBranchDn(directoryId, req.branchDn())) {
            throw new ConflictException(
                    "An attribute profile for branch '" + req.branchDn()
                    + "' already exists in this directory");
        }

        if (req.isDefault() &&
                profileRepo.findByDirectoryIdAndIsDefaultTrue(directoryId).isPresent()) {
            throw new ConflictException(
                    "A default attribute profile already exists for this directory");
        }

        AttributeProfile profile = new AttributeProfile();
        profile.setDirectory(dir);
        profile.setTenant(dir.getTenant());
        profile.setBranchDn(req.branchDn());
        profile.setDisplayName(req.displayName());
        profile.setDefault(req.isDefault());
        profile = profileRepo.save(profile);

        List<AttributeProfileEntry> entries = saveEntries(profile, req.entries());
        return toDto(profile, entries);
    }

    @Transactional
    public AttributeProfileDto update(UUID directoryId, UUID profileId,
                                       CreateAttributeProfileRequest req,
                                       AuthPrincipal principal) {
        loadDirectory(directoryId, principal);
        AttributeProfile profile = findProfile(profileId, directoryId, principal);

        // branchDn uniqueness check (only if changed)
        if (!profile.getBranchDn().equals(req.branchDn()) &&
                profileRepo.existsByDirectoryIdAndBranchDn(directoryId, req.branchDn())) {
            throw new ConflictException(
                    "An attribute profile for branch '" + req.branchDn()
                    + "' already exists in this directory");
        }

        // default-profile uniqueness check (only if we're promoting this profile)
        if (req.isDefault() && !profile.isDefault()) {
            profileRepo.findByDirectoryIdAndIsDefaultTrue(directoryId)
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(profileId)) {
                            throw new ConflictException(
                                    "A default attribute profile already exists for this directory");
                        }
                    });
        }

        profile.setBranchDn(req.branchDn());
        profile.setDisplayName(req.displayName());
        profile.setDefault(req.isDefault());
        profile = profileRepo.save(profile);

        entryRepo.deleteAllByProfileId(profileId);
        List<AttributeProfileEntry> entries = saveEntries(profile, req.entries());
        return toDto(profile, entries);
    }

    @Transactional
    public void delete(UUID directoryId, UUID profileId, AuthPrincipal principal) {
        loadDirectory(directoryId, principal);
        findProfile(profileId, directoryId, principal); // validates access
        entryRepo.deleteAllByProfileId(profileId);
        profileRepo.deleteById(profileId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private DirectoryConnection loadDirectory(UUID directoryId, AuthPrincipal principal) {
        if (principal.isSuperadmin()) {
            return dirRepo.findById(directoryId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "DirectoryConnection", directoryId));
        }
        return dirRepo.findByIdAndTenantId(directoryId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DirectoryConnection", directoryId));
    }

    private AttributeProfile findProfile(UUID profileId, UUID directoryId,
                                          AuthPrincipal principal) {
        AttributeProfile profile;
        if (principal.isSuperadmin()) {
            profile = profileRepo.findById(profileId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "AttributeProfile", profileId));
        } else {
            // Tenant-scoped lookup: fall back to findById then check tenant
            profile = profileRepo.findById(profileId)
                    .filter(p -> p.getTenant().getId().equals(principal.tenantId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "AttributeProfile", profileId));
        }
        if (!profile.getDirectory().getId().equals(directoryId)) {
            throw new ResourceNotFoundException("AttributeProfile", profileId);
        }
        return profile;
    }

    private List<AttributeProfileEntry> saveEntries(AttributeProfile profile,
                                                     List<UpsertAttributeProfileEntryRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            return List.of();
        }
        return reqs.stream()
                .map(r -> {
                    AttributeProfileEntry entry = new AttributeProfileEntry();
                    entry.setProfile(profile);
                    entry.setAttributeName(r.attributeName());
                    entry.setCustomLabel(r.customLabel());
                    entry.setRequiredOnCreate(r.requiredOnCreate());
                    entry.setEnabledOnEdit(r.enabledOnEdit());
                    entry.setInputType(r.inputType() != null ? r.inputType() : InputType.TEXT);
                    entry.setDisplayOrder(r.displayOrder());
                    entry.setVisibleInListView(r.visibleInListView());
                    return entryRepo.save(entry);
                })
                .toList();
    }

    private AttributeProfileDto toDto(AttributeProfile p,
                                       List<AttributeProfileEntry> entries) {
        List<AttributeProfileEntryDto> entryDtos = entries.stream()
                .map(e -> new AttributeProfileEntryDto(
                        e.getId(),
                        e.getAttributeName(),
                        e.getCustomLabel(),
                        e.isRequiredOnCreate(),
                        e.isEnabledOnEdit(),
                        e.getInputType(),
                        e.getDisplayOrder(),
                        e.isVisibleInListView(),
                        e.getCreatedAt(),
                        e.getUpdatedAt()))
                .toList();
        return new AttributeProfileDto(
                p.getId(),
                p.getDirectory().getId(),
                p.getBranchDn(),
                p.getDisplayName(),
                p.isDefault(),
                entryDtos,
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
