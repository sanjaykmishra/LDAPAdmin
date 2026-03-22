package com.ldapadmin.repository;

import com.ldapadmin.entity.ProfileLifecyclePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfileLifecyclePolicyRepository extends JpaRepository<ProfileLifecyclePolicy, UUID> {

    Optional<ProfileLifecyclePolicy> findByProfileId(UUID profileId);

    void deleteByProfileId(UUID profileId);
}
