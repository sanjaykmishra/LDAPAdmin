package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.auditor.AuditorLinkDto;
import com.ldapadmin.dto.auditor.CreateAuditorLinkRequest;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.AuditorLink;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.AuditorLinkRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditorLinkServiceTest {

    @Mock private AuditorLinkRepository auditorLinkRepo;
    @Mock private DirectoryConnectionRepository directoryRepo;
    @Mock private AccountRepository accountRepo;
    @Mock private CryptoService cryptoService;
    @Mock private AuditService auditService;

    private AuditorLinkService service;

    private final UUID directoryId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private DirectoryConnection directory;
    private Account account;
    private AuthPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new AuditorLinkService(
                auditorLinkRepo, directoryRepo, accountRepo,
                cryptoService, auditService);

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Directory");

        account = new Account();
        account.setId(accountId);
        account.setUsername("admin");

        principal = new AuthPrincipal(PrincipalType.SUPERADMIN, accountId, "admin");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_generatesTokenAndSavesLink() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(accountId)).thenReturn(Optional.of(account));
        when(cryptoService.generateToken()).thenReturn("test-token-abc");
        when(cryptoService.hmacSha256(any(byte[].class))).thenReturn("hmac-signature-hex");
        when(auditorLinkRepo.save(any(AuditorLink.class))).thenAnswer(inv -> {
            AuditorLink link = inv.getArgument(0);
            link.setId(UUID.randomUUID());
            link.setCreatedAt(OffsetDateTime.now());
            return link;
        });

        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                "Q1 SOC 2 Audit", List.of(UUID.randomUUID()),
                true, false, true, null, null, 30);

        AuditorLinkDto result = service.create(directoryId, request, principal);

        assertThat(result.token()).isEqualTo("test-token-abc");
        assertThat(result.label()).isEqualTo("Q1 SOC 2 Audit");
        assertThat(result.directoryId()).isEqualTo(directoryId);
        assertThat(result.includeSod()).isTrue();
        assertThat(result.includeEntitlements()).isFalse();
        assertThat(result.includeAuditEvents()).isTrue();
        assertThat(result.createdBy()).isEqualTo("admin");

        // Verify HMAC was computed
        verify(cryptoService).hmacSha256(any(byte[].class));
        // Verify audit event recorded
        verify(auditService).record(eq(principal), eq(directoryId), any(), isNull(), any());
    }

    @Test
    void create_setsExpiryBasedOnDays() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(accountId)).thenReturn(Optional.of(account));
        when(cryptoService.generateToken()).thenReturn("token");
        when(cryptoService.hmacSha256(any(byte[].class))).thenReturn("sig");
        when(auditorLinkRepo.save(any(AuditorLink.class))).thenAnswer(inv -> {
            AuditorLink link = inv.getArgument(0);
            link.setId(UUID.randomUUID());
            link.setCreatedAt(OffsetDateTime.now());
            return link;
        });

        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                null, List.of(), true, false, true, null, null, 90);

        service.create(directoryId, request, principal);

        ArgumentCaptor<AuditorLink> captor = ArgumentCaptor.forClass(AuditorLink.class);
        verify(auditorLinkRepo).save(captor.capture());
        AuditorLink saved = captor.getValue();

        assertThat(saved.getExpiresAt()).isAfter(OffsetDateTime.now().plusDays(89));
        assertThat(saved.getExpiresAt()).isBefore(OffsetDateTime.now().plusDays(91));
    }

    @Test
    void create_directoryNotFound_throws() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.empty());

        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                null, List.of(), true, false, true, null, null, 30);

        assertThatThrownBy(() -> service.create(directoryId, request, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── revoke ────────────────────────────────────────────────────────────────

    @Test
    void revoke_setsRevokedFlagAndTimestamp() {
        UUID linkId = UUID.randomUUID();
        AuditorLink link = AuditorLink.builder()
                .directory(directory)
                .token("token")
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .hmacSignature("sig")
                .createdBy(account)
                .build();
        link.setId(linkId);

        when(auditorLinkRepo.findById(linkId)).thenReturn(Optional.of(link));
        when(auditorLinkRepo.save(any(AuditorLink.class))).thenAnswer(inv -> inv.getArgument(0));

        service.revoke(linkId, principal);

        assertThat(link.isRevoked()).isTrue();
        assertThat(link.getRevokedAt()).isNotNull();
        verify(auditService).record(eq(principal), eq(directoryId), any(), isNull(), any());
    }

    @Test
    void revoke_alreadyRevoked_isIdempotent() {
        UUID linkId = UUID.randomUUID();
        AuditorLink link = AuditorLink.builder()
                .directory(directory)
                .token("token")
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .hmacSignature("sig")
                .createdBy(account)
                .revoked(true)
                .build();
        link.setId(linkId);
        link.setRevokedAt(OffsetDateTime.now().minusHours(1));
        OffsetDateTime originalRevokedAt = link.getRevokedAt();

        when(auditorLinkRepo.findById(linkId)).thenReturn(Optional.of(link));

        service.revoke(linkId, principal);

        // Should not save or record audit event — it's a no-op
        verify(auditorLinkRepo, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
        assertThat(link.getRevokedAt()).isEqualTo(originalRevokedAt);
    }

    @Test
    void revoke_linkNotFound_throws() {
        UUID linkId = UUID.randomUUID();
        when(auditorLinkRepo.findById(linkId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(linkId, principal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_returnsDtosForDirectory() {
        AuditorLink link = AuditorLink.builder()
                .directory(directory)
                .token("token")
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .hmacSignature("sig")
                .createdBy(account)
                .build();
        link.setId(UUID.randomUUID());
        link.setCreatedAt(OffsetDateTime.now());

        when(auditorLinkRepo.findByDirectoryIdOrderByCreatedAtDesc(directoryId))
                .thenReturn(List.of(link));

        List<AuditorLinkDto> result = service.list(directoryId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).token()).isEqualTo("token");
        assertThat(result.get(0).directoryId()).isEqualTo(directoryId);
    }

    // ── validateToken ─────────────────────────────────────────────────────────

    @Test
    void validateToken_validToken_returnsLinkAndIncrementsAccess() {
        AuditorLink link = AuditorLink.builder()
                .directory(directory)
                .token("valid-token")
                .campaignIds(List.of())
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .hmacSignature("expected-hmac")
                .createdBy(account)
                .build();
        link.setId(UUID.randomUUID());

        when(auditorLinkRepo.findByTokenAndRevokedFalse("valid-token"))
                .thenReturn(Optional.of(link));

        // Reconstruct the same signature input that create() would have used
        String signatureInput = link.getToken() + link.getDirectory().getId()
                + link.getCampaignIds() + link.isIncludeSod()
                + link.isIncludeEntitlements() + link.isIncludeAuditEvents()
                + link.getExpiresAt().toInstant().getEpochSecond();
        when(cryptoService.hmacSha256(eq(signatureInput.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .thenReturn("expected-hmac");

        when(auditorLinkRepo.save(any(AuditorLink.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditorLink result = service.validateToken("valid-token");

        assertThat(result.getAccessCount()).isEqualTo(1);
        assertThat(result.getLastAccessedAt()).isNotNull();
    }

    @Test
    void validateToken_tokenNotFound_throws() {
        when(auditorLinkRepo.findByTokenAndRevokedFalse("bad-token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateToken("bad-token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validateToken_expiredToken_throws() {
        AuditorLink link = AuditorLink.builder()
                .directory(directory)
                .token("expired-token")
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .hmacSignature("sig")
                .createdBy(account)
                .build();

        when(auditorLinkRepo.findByTokenAndRevokedFalse("expired-token"))
                .thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.validateToken("expired-token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validateToken_hmacMismatch_throws() {
        AuditorLink link = AuditorLink.builder()
                .directory(directory)
                .token("tampered-token")
                .campaignIds(List.of())
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .hmacSignature("original-hmac")
                .createdBy(account)
                .build();

        when(auditorLinkRepo.findByTokenAndRevokedFalse("tampered-token"))
                .thenReturn(Optional.of(link));
        when(cryptoService.hmacSha256(any(byte[].class)))
                .thenReturn("different-hmac"); // Doesn't match stored signature

        assertThatThrownBy(() -> service.validateToken("tampered-token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
