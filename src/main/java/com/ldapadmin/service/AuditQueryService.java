package com.ldapadmin.service;

import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditEventRepository auditRepo;

    /**
     * Paginated, multi-filter query. All filter params are optional.
     */
    @Transactional(readOnly = true)
    public Page<AuditEventResponse> query(
            UUID directoryId,
            UUID actorId,
            AuditAction action,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size) {

        PageRequest pageable = PageRequest.of(page, clampSize(size),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return auditRepo.findAll(directoryId, actorId, action, from, to, pageable)
                .map(AuditEventResponse::from);
    }

    private int clampSize(int requested) {
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
