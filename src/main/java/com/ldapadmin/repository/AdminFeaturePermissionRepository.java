package com.ldapadmin.repository;

import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.enums.FeatureKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminFeaturePermissionRepository extends JpaRepository<AdminFeaturePermission, UUID> {

    List<AdminFeaturePermission> findAllByAdminAccountId(UUID adminAccountId);

    Optional<AdminFeaturePermission> findByAdminAccountIdAndFeatureKey(UUID adminAccountId, FeatureKey featureKey);

    boolean existsByAdminAccountIdAndFeatureKeyAndEnabledTrue(UUID adminAccountId, FeatureKey featureKey);

    void deleteByAdminAccountIdAndFeatureKey(UUID adminAccountId, FeatureKey featureKey);

    void deleteAllByAdminAccountId(UUID adminAccountId);
}
