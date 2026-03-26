package com.ldapadmin.repository;

import com.ldapadmin.entity.AuditorLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditorLinkRepository extends JpaRepository<AuditorLink, UUID> {

    /** Look up a non-revoked link by token, eagerly fetching directory and createdBy. */
    @Query("SELECT l FROM AuditorLink l JOIN FETCH l.directory JOIN FETCH l.createdBy "
            + "WHERE l.token = :token AND l.revoked = false")
    Optional<AuditorLink> findByTokenAndRevokedFalse(@Param("token") String token);

    /** All links for a directory, newest first (admin management view). */
    @Query("SELECT l FROM AuditorLink l JOIN FETCH l.directory JOIN FETCH l.createdBy "
            + "WHERE l.directory.id = :directoryId ORDER BY l.createdAt DESC")
    List<AuditorLink> findByDirectoryIdOrderByCreatedAtDesc(@Param("directoryId") UUID directoryId);
}
