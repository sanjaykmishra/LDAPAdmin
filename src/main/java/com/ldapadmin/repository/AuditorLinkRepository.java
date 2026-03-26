package com.ldapadmin.repository;

import com.ldapadmin.entity.AuditorLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditorLinkRepository extends JpaRepository<AuditorLink, UUID> {

    /** Look up a non-revoked link by token (uses the partial index). */
    Optional<AuditorLink> findByTokenAndRevokedFalse(String token);

    /** All links for a directory, newest first (admin management view). */
    List<AuditorLink> findByDirectoryIdOrderByCreatedAtDesc(UUID directoryId);
}
