package com.ldapadmin.service;

import com.ldapadmin.dto.realm.RealmRequest;
import com.ldapadmin.dto.realm.RealmResponse;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.RealmObjectclass;
import com.ldapadmin.entity.UserForm;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.RealmObjectclassRepository;
import com.ldapadmin.repository.RealmRepository;
import com.ldapadmin.repository.UserFormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CRUD for realms.
 *
 * <p>A realm is a logical partition of a directory connection that defines the
 * LDAP subtrees used for user/group entries and links to one or more user forms
 * that drive the user creation UI.</p>
 */
@Service
@RequiredArgsConstructor
public class RealmService {

    private final RealmRepository               realmRepo;
    private final DirectoryConnectionRepository dirRepo;
    private final RealmObjectclassRepository    realmOcRepo;
    private final UserFormRepository            userFormRepo;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RealmResponse> listAll() {
        return realmRepo.findAllByOrderByDirectoryIdAscDisplayOrderAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RealmResponse> listByDirectory(UUID directoryId) {
        requireDirectory(directoryId);
        return realmRepo.findAllByDirectoryIdOrderByDisplayOrderAsc(directoryId)
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
        syncUserForms(realm, req.userFormIds());
        return toResponse(realm);
    }

    @Transactional
    public RealmResponse update(UUID directoryId, UUID realmId, RealmRequest req) {
        Realm realm = requireRealm(directoryId, realmId);
        applyRequest(realm, req);
        realm = realmRepo.save(realm);
        syncUserForms(realm, req.userFormIds());
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
        realm.setDisplayOrder(req.displayOrder());
    }

    /**
     * Syncs the realm_objectclasses entries that link a realm to user forms.
     * Removes links to forms no longer in the list and adds new ones.
     */
    private void syncUserForms(Realm realm, List<UUID> userFormIds) {
        List<RealmObjectclass> existing = realmOcRepo.findAllByRealmId(realm.getId());
        List<UUID> desired = userFormIds != null ? userFormIds : List.of();

        // Index existing entries by user form id
        Map<UUID, RealmObjectclass> existingByFormId = existing.stream()
                .filter(oc -> oc.getUserForm() != null)
                .collect(Collectors.toMap(oc -> oc.getUserForm().getId(), oc -> oc));

        // Remove entries not in the desired list
        Set<UUID> desiredSet = new HashSet<>(desired);
        for (var entry : existingByFormId.entrySet()) {
            if (!desiredSet.contains(entry.getKey())) {
                realmOcRepo.delete(entry.getValue());
            }
        }

        // Add new entries
        for (UUID formId : desired) {
            if (!existingByFormId.containsKey(formId)) {
                UserForm form = userFormRepo.findById(formId)
                        .orElseThrow(() -> new ResourceNotFoundException("UserForm", formId));
                RealmObjectclass oc = new RealmObjectclass();
                oc.setRealm(realm);
                oc.setUserForm(form);
                realmOcRepo.save(oc);
            }
        }

        // Remove entries with no user form link (orphans from old data)
        existing.stream()
                .filter(oc -> oc.getUserForm() == null)
                .forEach(realmOcRepo::delete);
    }
}
