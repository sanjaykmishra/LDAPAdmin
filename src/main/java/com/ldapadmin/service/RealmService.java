package com.ldapadmin.service;

import com.ldapadmin.dto.realm.RealmRequest;
import com.ldapadmin.dto.realm.RealmResponse;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.RealmAuxiliaryObjectclass;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.RealmRepository;
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

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RealmResponse> listByDirectory(UUID directoryId) {
        requireDirectory(directoryId);
        return realmRepo.findAllByDirectoryIdOrderByDisplayOrderAsc(directoryId)
                .stream().map(RealmResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RealmResponse get(UUID directoryId, UUID realmId) {
        return RealmResponse.from(requireRealm(directoryId, realmId));
    }

    @Transactional
    public RealmResponse create(UUID directoryId, RealmRequest req) {
        DirectoryConnection dir = requireDirectory(directoryId);
        Realm realm = new Realm();
        realm.setDirectory(dir);
        applyRequest(realm, req);
        return RealmResponse.from(realmRepo.save(realm));
    }

    @Transactional
    public RealmResponse update(UUID directoryId, UUID realmId, RealmRequest req) {
        Realm realm = requireRealm(directoryId, realmId);
        realm.getAuxiliaryObjectclasses().clear();
        applyRequest(realm, req);
        return RealmResponse.from(realmRepo.save(realm));
    }

    @Transactional
    public void delete(UUID directoryId, UUID realmId) {
        Realm realm = requireRealm(directoryId, realmId);
        realmRepo.delete(realm);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
}
