package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.auditor.AuditorLinkDto;
import com.ldapadmin.dto.auditor.CreateAuditorLinkRequest;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.AuditorLink;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.AuditorLinkRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the lifecycle of auditor links: creation, revocation, listing,
 * and token validation for portal access.
 *
 * <p>The token itself (256-bit cryptographically random, Base64URL-encoded)
 * is the sole credential. Scope and expiry are enforced by reading from
 * the database at access time.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditorLinkService {

    private final AuditorLinkRepository auditorLinkRepo;
    private final DirectoryConnectionRepository directoryRepo;
    private final AccountRepository accountRepo;
    private final CryptoService cryptoService;
    private final AuditService auditService;

    /**
     * Creates a new auditor link with a cryptographically random token.
     */
    @Transactional
    public AuditorLinkDto create(UUID directoryId, CreateAuditorLinkRequest request,
                                  AuthPrincipal principal) {
        DirectoryConnection directory = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Directory", directoryId));
        Account creator = accountRepo.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", principal.id()));

        String token = cryptoService.generateToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(request.expiryDays());

        AuditorLink link = AuditorLink.builder()
                .directory(directory)
                .token(token)
                .label(request.label())
                .campaignIds(request.campaignIds())
                .includeSod(request.includeSod())
                .includeEntitlements(request.includeEntitlements())
                .includeAuditEvents(request.includeAuditEvents())
                .dataFrom(request.dataFrom())
                .dataTo(request.dataTo())
                .expiresAt(expiresAt)
                .createdBy(creator)
                .build();

        AuditorLink saved = auditorLinkRepo.save(link);

        auditService.record(principal, directoryId,
                AuditAction.AUDITOR_LINK_CREATED, null,
                Map.of("linkId", saved.getId().toString(),
                        "label", request.label() != null ? request.label() : "",
                        "expiryDays", String.valueOf(request.expiryDays())));

        log.info("Auditor link created: id={}, label='{}', expiresAt={}, createdBy={}",
                saved.getId(), request.label(), expiresAt, principal.username());

        return AuditorLinkDto.from(saved);
    }

    /**
     * Revokes an auditor link, immediately preventing further access.
     * Idempotent — calling on an already-revoked link is a no-op.
     */
    @Transactional
    public void revoke(UUID linkId, AuthPrincipal principal) {
        AuditorLink link = auditorLinkRepo.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditorLink", linkId));

        if (link.isRevoked()) {
            log.info("Auditor link already revoked: id={}", linkId);
            return;
        }

        link.setRevoked(true);
        link.setRevokedAt(OffsetDateTime.now());
        auditorLinkRepo.save(link);

        auditService.record(principal, link.getDirectory().getId(),
                AuditAction.AUDITOR_LINK_REVOKED, null,
                Map.of("linkId", linkId.toString(),
                        "label", link.getLabel() != null ? link.getLabel() : ""));

        log.info("Auditor link revoked: id={}, revokedBy={}", linkId, principal.username());
    }

    /**
     * Lists all auditor links for a directory, newest first.
     */
    @Transactional(readOnly = true)
    public List<AuditorLinkDto> list(UUID directoryId) {
        return auditorLinkRepo.findByDirectoryIdOrderByCreatedAtDesc(directoryId)
                .stream()
                .map(AuditorLinkDto::from)
                .toList();
    }

    /**
     * Validates a token for portal access with optional IP/user-agent logging.
     */
    @Transactional
    public AuditorLink validateToken(String token, String clientIp, String userAgent) {
        AuditorLink link = validateToken(token);
        if (clientIp != null) {
            try {
                var systemPrincipal = new AuthPrincipal(
                        com.ldapadmin.auth.PrincipalType.SUPERADMIN, new UUID(0, 0), "auditor-portal");
                auditService.record(systemPrincipal, link.getDirectory().getId(),
                        AuditAction.AUDITOR_LINK_ACCESSED, null,
                        Map.of("linkId", link.getId().toString(),
                                "clientIp", clientIp,
                                "userAgent", userAgent != null ? userAgent : ""));
            } catch (Exception e) {
                log.warn("Failed to record portal access audit event: {}", e.getMessage());
            }
        }
        return link;
    }

    /**
     * Validates a token for portal access. Checks that the link exists,
     * is not revoked, and is not expired. On success, increments the
     * access counter.
     *
     * @return the validated AuditorLink entity
     * @throws ResourceNotFoundException if the token is invalid, revoked, or expired
     */
    @Transactional
    public AuditorLink validateToken(String token) {
        AuditorLink link = auditorLinkRepo.findByTokenAndRevokedFalse(token)
                .orElseThrow(() -> new ResourceNotFoundException("Auditor link not found"));

        if (link.isExpired()) {
            throw new ResourceNotFoundException("Auditor link not found");
        }

        link.setAccessCount(link.getAccessCount() + 1);
        link.setLastAccessedAt(OffsetDateTime.now());
        auditorLinkRepo.save(link);

        return link;
    }
}
