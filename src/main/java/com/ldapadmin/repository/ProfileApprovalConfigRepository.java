package com.ldapadmin.repository;

import com.ldapadmin.entity.ProfileApprovalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfileApprovalConfigRepository extends JpaRepository<ProfileApprovalConfig, UUID> {

    Optional<ProfileApprovalConfig> findByProfileId(UUID profileId);

    void deleteByProfileId(UUID profileId);
}
