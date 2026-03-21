package com.ldapadmin.repository;

import com.ldapadmin.entity.RealmSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RealmSettingRepository extends JpaRepository<RealmSetting, UUID> {

    Optional<RealmSetting> findByRealmIdAndKey(UUID realmId, String key);

    List<RealmSetting> findAllByRealmId(UUID realmId);

    void deleteByRealmIdAndKey(UUID realmId, String key);
}
