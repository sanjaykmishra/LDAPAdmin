package com.ldapadmin.controller;

import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit log query endpoint.
 *
 * <pre>
 *   GET /api/v1/audit — paginated, filterable audit log
 * </pre>
 *
 * <p>All filter parameters are optional.  Results are paginated and ordered
 * by {@code occurredAt DESC}.</p>
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditQueryService queryService;

    /**
     * Returns audit events with optional filters.
     *
     * @param directoryId filter by directory (optional)
     * @param actorId     filter by admin actor UUID (optional)
     * @param action      filter by action (optional)
     * @param from        lower bound on {@code occurredAt} (ISO-8601, optional)
     * @param to          upper bound on {@code occurredAt} (ISO-8601, optional)
     * @param page        zero-based page number (default 0)
     * @param size        page size, 1–200 (default 50)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public Page<AuditEventResponse> get(
            @RequestParam(required = false) UUID directoryId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        return queryService.query(directoryId, actorId, action, from, to, page, size);
    }
}
