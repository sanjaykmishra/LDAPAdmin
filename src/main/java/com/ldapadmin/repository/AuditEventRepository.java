package com.ldapadmin.repository;

import com.ldapadmin.entity.AuditEvent;
import com.ldapadmin.entity.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * Paginated, multi-filter query for tenant-scoped audit log access.
     * All filter params are optional (null = no filter on that dimension).
     */
    @Query("""
            SELECT e FROM AuditEvent e
            WHERE e.tenantId = :tenantId
              AND (:directoryId IS NULL OR e.directoryId = :directoryId)
              AND (:actorId     IS NULL OR e.actorId     = :actorId)
              AND (:action      IS NULL OR e.action      = :action)
              AND (:from        IS NULL OR e.occurredAt >= :from)
              AND (:to          IS NULL OR e.occurredAt <= :to)
            ORDER BY e.occurredAt DESC
            """)
    Page<AuditEvent> findByTenant(
            @Param("tenantId")    UUID tenantId,
            @Param("directoryId") UUID directoryId,
            @Param("actorId")     UUID actorId,
            @Param("action")      AuditAction action,
            @Param("from")        OffsetDateTime from,
            @Param("to")          OffsetDateTime to,
            Pageable pageable);

    /**
     * Cross-tenant query for superadmin audit log browsing.
     */
    @Query("""
            SELECT e FROM AuditEvent e
            WHERE (:tenantId    IS NULL OR e.tenantId    = :tenantId)
              AND (:directoryId IS NULL OR e.directoryId = :directoryId)
              AND (:actorId     IS NULL OR e.actorId     = :actorId)
              AND (:action      IS NULL OR e.action      = :action)
              AND (:from        IS NULL OR e.occurredAt >= :from)
              AND (:to          IS NULL OR e.occurredAt <= :to)
            ORDER BY e.occurredAt DESC
            """)
    Page<AuditEvent> findAll(
            @Param("tenantId")    UUID tenantId,
            @Param("directoryId") UUID directoryId,
            @Param("actorId")     UUID actorId,
            @Param("action")      AuditAction action,
            @Param("from")        OffsetDateTime from,
            @Param("to")          OffsetDateTime to,
            Pageable pageable);

    /** Used by the changelog reader to skip already-processed records. */
    boolean existsByDirectoryIdAndChangelogChangeNumber(UUID directoryId, String changeNumber);
}
