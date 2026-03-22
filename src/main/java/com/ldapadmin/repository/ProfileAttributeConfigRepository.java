package com.ldapadmin.repository;

import com.ldapadmin.entity.ProfileAttributeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileAttributeConfigRepository extends JpaRepository<ProfileAttributeConfig, UUID> {

    List<ProfileAttributeConfig> findAllByProfileIdOrderByDisplayOrderAsc(UUID profileId);

    void deleteAllByProfileId(UUID profileId);

    void flush();
}
