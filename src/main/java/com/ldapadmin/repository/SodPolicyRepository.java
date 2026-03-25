package com.ldapadmin.repository;

import com.ldapadmin.entity.SodPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SodPolicyRepository extends JpaRepository<SodPolicy, UUID> {

    List<SodPolicy> findByDirectoryId(UUID directoryId);

    List<SodPolicy> findByEnabledTrue();

    List<SodPolicy> findByDirectoryIdAndEnabledTrue(UUID directoryId);

    @Query("SELECT COUNT(p) > 0 FROM SodPolicy p WHERE p.directory.id = :directoryId AND p.enabled = true " +
           "AND ((LOWER(p.groupADn) = LOWER(:groupADn) AND LOWER(p.groupBDn) = LOWER(:groupBDn)) " +
           "  OR (LOWER(p.groupADn) = LOWER(:groupBDn) AND LOWER(p.groupBDn) = LOWER(:groupADn)))")
    boolean existsDuplicateGroupPair(@Param("directoryId") UUID directoryId,
                                     @Param("groupADn") String groupADn,
                                     @Param("groupBDn") String groupBDn);
}
