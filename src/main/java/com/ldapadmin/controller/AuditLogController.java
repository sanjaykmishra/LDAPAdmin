package com.ldapadmin.controller;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit log query endpoints.
 *
 * <pre>
 *   GET /api/audit          — tenant-scoped log (any authenticated admin)
 *   GET /api/superadmin/audit — cross-tenant log (superadmin only)
 * </pre>
 *
 * <p>All filter parameters are optional.  Results are paginated and ordered
 * by {@code occurredAt DESC}.</p>
 */
@RestController
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditQueryService queryService;

    // ── Tenant-scoped ─────────────────────────────────────────────────────────

    /**
     * Returns audit events for the authenticated admin's tenant.
     *
     * @param directoryId filter by directory (optional)
     * @param actorId     filter by admin actor UUID (optional)
     * @param action      filter by action string, e.g. {@code user.create} (optional)
     * @param from        lower bound on {@code occurredAt} (ISO-8601, optional)
     * @param to          upper bound on {@code occurredAt} (ISO-8601, optional)
     * @param page        zero-based page number (default 0)
     * @param size        page size, 1–200 (default 50)
     */
    @GetMapping("/api/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public Page<AuditEventResponse> getForTenant(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) UUID directoryId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        return queryService.queryForTenant(
                principal.tenantId(), directoryId, actorId, action, from, to, page, size);
    }

    // ── Superadmin cross-tenant ───────────────────────────────────────────────

    /**
     * Returns audit events across all tenants (superadmin only).
     * {@code tenantId} can be supplied to scope to a specific tenant.
     */
    @GetMapping("/api/superadmin/audit")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public Page<AuditEventResponse> getAll(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID directoryId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        return queryService.queryAll(
                tenantId, directoryId, actorId, action, from, to, page, size);
    }
}
