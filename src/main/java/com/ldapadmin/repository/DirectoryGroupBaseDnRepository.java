package com.ldapadmin.repository;

import com.ldapadmin.entity.DirectoryGroupBaseDn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DirectoryGroupBaseDnRepository extends JpaRepository<DirectoryGroupBaseDn, UUID> {

    List<DirectoryGroupBaseDn> findAllByDirectoryIdOrderByDisplayOrderAsc(UUID directoryId);

    void deleteAllByDirectoryId(UUID directoryId);
}
