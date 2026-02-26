package com.ldapadmin.repository;

import com.ldapadmin.entity.Realm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RealmRepository extends JpaRepository<Realm, UUID> {

    List<Realm> findAllByDirectoryIdOrderByDisplayOrderAsc(UUID directoryId);

    Optional<Realm> findByIdAndDirectoryId(UUID id, UUID directoryId);

    boolean existsByIdAndDirectoryId(UUID id, UUID directoryId);

    void deleteAllByDirectoryId(UUID directoryId);
}
