package com.ldapadmin.service;

import com.ldapadmin.dto.realm.RealmRequest;
import com.ldapadmin.dto.realm.RealmResponse;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.RealmObjectclass;
import com.ldapadmin.entity.UserTemplate;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.RealmObjectclassRepository;
import com.ldapadmin.repository.RealmRepository;
import com.ldapadmin.repository.UserTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CRUD for realms.
 *
 * <p>A realm is a logical partition of a directory connection that defines the
 * LDAP subtrees used for user/group entries and links to one or more user templates
 * that drive the user creation UI.</p>
 */
@Service
@RequiredArgsConstructor
public class RealmService {

    private final RealmRepository               realmRepo;
    private final DirectoryConnectionRepository dirRepo;
    private final RealmObjectclassRepository    realmOcRepo;
    private final UserTemplateRepository        userTemplateRepo;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RealmResponse> listAll() {
        return realmRepo.findAllByOrderByDirectoryIdAscNameAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RealmResponse> listByDirectory(UUID directoryId) {
        requireDirectory(directoryId);
        return realmRepo.findAllByDirectoryIdOrderByNameAsc(directoryId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RealmResponse get(UUID directoryId, UUID realmId) {
        return toResponse(requireRealm(directoryId, realmId));
    }

    @Transactional
    public RealmResponse create(UUID directoryId, RealmRequest req) {
        DirectoryConnection dir = requireDirectory(directoryId);
        Realm realm = new Realm();
        realm.setDirectory(dir);
        applyRequest(realm, req);
        realm = realmRepo.save(realm);
        syncUserTemplates(realm, req.userTemplateIds());
        return toResponse(realm);
    }

    @Transactional
    public RealmResponse update(UUID directoryId, UUID realmId, RealmRequest req) {
        Realm realm = requireRealm(directoryId, realmId);
        applyRequest(realm, req);
        realm = realmRepo.save(realm);
        syncUserTemplates(realm, req.userTemplateIds());
        return toResponse(realm);
    }

    @Transactional
    public void delete(UUID directoryId, UUID realmId) {
        Realm realm = requireRealm(directoryId, realmId);
        realmRepo.delete(realm);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private RealmResponse toResponse(Realm realm) {
        List<RealmObjectclass> ocs = realmOcRepo.findAllByRealmId(realm.getId());
        return RealmResponse.from(realm, ocs);
    }

    private DirectoryConnection requireDirectory(UUID directoryId) {
        return dirRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
    }

    private Realm requireRealm(UUID directoryId, UUID realmId) {
        return realmRepo.findByIdAndDirectoryId(realmId, directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Realm", realmId));
    }

    private void applyRequest(Realm realm, RealmRequest req) {
        realm.setName(req.name());
        realm.setUserBaseDn(req.userBaseDn());
        realm.setGroupBaseDn(req.groupBaseDn());
    }

    /**
     * Syncs the realm_objectclasses entries that link a realm to user templates.
     * Removes links to templates no longer in the list and adds new ones.
     */
    private void syncUserTemplates(Realm realm, List<UUID> userTemplateIds) {
        List<RealmObjectclass> existing = realmOcRepo.findAllByRealmId(realm.getId());
        List<UUID> desired = userTemplateIds != null ? userTemplateIds : List.of();

        // Index existing entries by user template id
        Map<UUID, RealmObjectclass> existingByTemplateId = existing.stream()
                .filter(oc -> oc.getUserTemplate() != null)
                .collect(Collectors.toMap(oc -> oc.getUserTemplate().getId(), oc -> oc));

        // Remove entries not in the desired list
        Set<UUID> desiredSet = new HashSet<>(desired);
        for (var entry : existingByTemplateId.entrySet()) {
            if (!desiredSet.contains(entry.getKey())) {
                realmOcRepo.delete(entry.getValue());
            }
        }

        // Add new entries
        for (UUID templateId : desired) {
            if (!existingByTemplateId.containsKey(templateId)) {
                UserTemplate template = userTemplateRepo.findById(templateId)
                        .orElseThrow(() -> new ResourceNotFoundException("UserTemplate", templateId));
                RealmObjectclass oc = new RealmObjectclass();
                oc.setRealm(realm);
                oc.setUserTemplate(template);
                realmOcRepo.save(oc);
            }
        }

        // Remove entries with no user template link (orphans from old data)
        existing.stream()
                .filter(oc -> oc.getUserTemplate() == null)
                .forEach(realmOcRepo::delete);
    }
}
