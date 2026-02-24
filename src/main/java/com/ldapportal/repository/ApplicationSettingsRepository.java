package com.ldapportal.repository;

import com.ldapportal.entity.ApplicationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationSettingsRepository extends JpaRepository<ApplicationSettings, UUID> {

    Optional<ApplicationSettings> findByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);
}
