package com.ldapadmin.repository;

import com.ldapadmin.entity.SodPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SodPolicyRepository extends JpaRepository<SodPolicy, UUID> {

    List<SodPolicy> findByDirectoryId(UUID directoryId);

    List<SodPolicy> findByEnabledTrue();

    List<SodPolicy> findByDirectoryIdAndEnabledTrue(UUID directoryId);
}
