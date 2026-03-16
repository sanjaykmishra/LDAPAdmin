package com.ldapadmin.service;

import com.ldapadmin.dto.realm.RealmRequest;
import com.ldapadmin.dto.realm.RealmResponse;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.RealmAuxiliaryObjectclass;
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

import java.util.List;
import java.util.UUID;

/**
 * CRUD for realms (§3.1).
 *
 * <p>A realm is a logical partition of a directory connection that defines the
 * LDAP subtrees and objectClasses used for user/group entries.  Admin permissions
 * are scoped to realms rather than to directories.</p>
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
        syncUserForm(realm, req.userFormId());
        return toResponse(realm);
    }

    @Transactional
    public RealmResponse update(UUID directoryId, UUID realmId, RealmRequest req) {
        Realm realm = requireRealm(directoryId, realmId);
        realm.getAuxiliaryObjectclasses().clear();
        applyRequest(realm, req);
        realm = realmRepo.save(realm);
        syncUserForm(realm, req.userFormId());
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
        realm.setPrimaryUserObjectclass(req.primaryUserObjectclass());
        realm.setDisplayOrder(req.displayOrder());

        if (req.auxiliaryObjectclasses() != null) {
            req.auxiliaryObjectclasses().forEach(aux -> {
                RealmAuxiliaryObjectclass entry = new RealmAuxiliaryObjectclass();
                entry.setRealm(realm);
                entry.setObjectclassName(aux.objectclassName());
                entry.setDisplayOrder(aux.displayOrder());
                realm.getAuxiliaryObjectclasses().add(entry);
            });
        }
    }

    /**
     * Syncs the realm_objectclasses entry that links a realm to a user form.
     * If userFormId is null, any existing link is removed.
     */
    private void syncUserForm(Realm realm, UUID userFormId) {
        List<RealmObjectclass> existing = realmOcRepo.findAllByRealmId(realm.getId());

        if (userFormId == null) {
            // Remove any existing objectclass entries that have a user form
            existing.stream()
                    .filter(oc -> oc.getUserForm() != null)
                    .forEach(realmOcRepo::delete);
            return;
        }

        UserForm form = userFormRepo.findById(userFormId)
                .orElseThrow(() -> new ResourceNotFoundException("UserForm", userFormId));

        // Find an existing entry to update, or create a new one
        RealmObjectclass oc = existing.stream()
                .filter(e -> e.getUserForm() != null)
                .findFirst()
                .orElseGet(() -> {
                    RealmObjectclass newOc = new RealmObjectclass();
                    newOc.setRealm(realm);
                    return newOc;
                });

        oc.setUserForm(form);
        realmOcRepo.save(oc);
    }
}
