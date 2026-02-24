package com.ldapportal.repository;

import com.ldapportal.entity.AttributeProfileEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttributeProfileEntryRepository extends JpaRepository<AttributeProfileEntry, UUID> {

    List<AttributeProfileEntry> findAllByProfileIdOrderByDisplayOrderAsc(UUID profileId);

    Optional<AttributeProfileEntry> findByProfileIdAndAttributeName(UUID profileId, String attributeName);

    List<AttributeProfileEntry> findAllByProfileIdAndVisibleInListViewTrue(UUID profileId);

    void deleteAllByProfileId(UUID profileId);
}
