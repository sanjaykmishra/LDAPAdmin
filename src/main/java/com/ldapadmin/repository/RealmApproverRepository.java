package com.ldapadmin.repository;

import com.ldapadmin.entity.RealmApprover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RealmApproverRepository extends JpaRepository<RealmApprover, UUID> {

    List<RealmApprover> findAllByRealmId(UUID realmId);

    @Query("SELECT ra FROM RealmApprover ra JOIN FETCH ra.adminAccount WHERE ra.realm.id = :realmId")
    List<RealmApprover> findAllByRealmIdWithAccount(UUID realmId);

    List<RealmApprover> findAllByAdminAccountId(UUID accountId);

    boolean existsByRealmIdAndAdminAccountId(UUID realmId, UUID accountId);

    void deleteAllByRealmId(UUID realmId);
}
