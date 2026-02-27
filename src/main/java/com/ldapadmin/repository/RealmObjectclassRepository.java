package com.ldapadmin.repository;

import com.ldapadmin.entity.RealmObjectclass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RealmObjectclassRepository extends JpaRepository<RealmObjectclass, UUID> {

    List<RealmObjectclass> findAllByRealmId(UUID realmId);

    Optional<RealmObjectclass> findByIdAndRealmId(UUID id, UUID realmId);

    void deleteAllByRealmId(UUID realmId);
}
