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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the lifecycle of auditor links: creation, revocation, listing,
 * and token validation for portal access.
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
     * Creates a new auditor link with a cryptographically random token and
     * HMAC signature covering the token, scope, and expiry.
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

        // Compute HMAC over (token + directoryId + scope + expiresAt)
        String signatureInput = token + directoryId + request.campaignIds()
                + request.includeSod() + request.includeEntitlements()
                + request.includeAuditEvents() + expiresAt;
        String hmac = cryptoService.hmacSha256(signatureInput.getBytes(StandardCharsets.UTF_8));

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
                .hmacSignature(hmac)
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
     * Validates a token for portal access. Checks that the link exists,
     * is not revoked, is not expired, and that the HMAC signature matches
     * (tamper detection). On success, increments the access counter.
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

        // Verify HMAC signature (tamper detection)
        String signatureInput = link.getToken() + link.getDirectory().getId()
                + link.getCampaignIds() + link.isIncludeSod()
                + link.isIncludeEntitlements() + link.isIncludeAuditEvents()
                + link.getExpiresAt();
        String expectedHmac = cryptoService.hmacSha256(
                signatureInput.getBytes(StandardCharsets.UTF_8));

        if (!MessageDigest.isEqual(
                expectedHmac.getBytes(StandardCharsets.UTF_8),
                link.getHmacSignature().getBytes(StandardCharsets.UTF_8))) {
            log.warn("HMAC mismatch for auditor link token — possible tampering");
            throw new ResourceNotFoundException("Auditor link not found");
        }

        // Update access tracking
        link.setAccessCount(link.getAccessCount() + 1);
        link.setLastAccessedAt(OffsetDateTime.now());
        auditorLinkRepo.save(link);

        return link;
    }
}
