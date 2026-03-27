package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.IpRateLimiter;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.auditor.AuditorLinkDto;
import com.ldapadmin.dto.auditor.CreateAuditorLinkRequest;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.AuditorLink;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.exception.TooManyRequestsException;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.AuditorLinkRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Full lifecycle integration tests for the auditor link feature.
 * Uses H2 in-memory database with the full Spring context.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditorLinkIntegrationTest {

    @Autowired private AuditorLinkService auditorLinkService;
    @Autowired private AuditorLinkRepository auditorLinkRepo;
    @Autowired private DirectoryConnectionRepository directoryRepo;
    @Autowired private AccountRepository accountRepo;
    @Autowired private IpRateLimiter ipRateLimiter;
    @Autowired private EntityManager em;

    private UUID directoryId;
    private UUID accountId;
    private AuthPrincipal principal;

    @BeforeEach
    void setUp() {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setDisplayName("Integration Test Dir");
        dir.setHost("localhost");
        dir.setPort(389);
        dir.setBindDn("cn=admin");
        dir.setBindPasswordEncrypted("enc");
        dir.setBaseDn("dc=test");
        em.persist(dir);

        Account account = new Account();
        account.setUsername("integ-test-" + UUID.randomUUID());
        account.setPasswordHash("hash");
        account.setRole(AccountRole.SUPERADMIN);
        em.persist(account);

        em.flush();

        directoryId = dir.getId();
        accountId = account.getId();
        principal = new AuthPrincipal(PrincipalType.SUPERADMIN, accountId, account.getUsername());
    }

    // ── Full lifecycle ────────────────────────────────────────────────────

    @Test
    void fullLifecycle_createAccessRevokeConfirm404() {
        // Create
        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                "Integration Test Link", List.of(), true, false, true,
                null, null, 30);
        AuditorLinkDto created = auditorLinkService.create(directoryId, request, principal);
        assertThat(created.token()).isNotBlank();
        assertThat(created.label()).isEqualTo("Integration Test Link");

        // Flush + clear to force DB round-trip (catches timestamp precision issues)
        em.flush();
        em.clear();

        // Access portal
        AuditorLink validated = auditorLinkService.validateToken(created.token());
        assertThat(validated.getAccessCount()).isEqualTo(1);

        // Access again → counter increments
        AuditorLink validated2 = auditorLinkService.validateToken(created.token());
        assertThat(validated2.getAccessCount()).isEqualTo(2);

        // List
        List<AuditorLinkDto> links = auditorLinkService.list(directoryId);
        assertThat(links).anyMatch(l -> l.id().equals(created.id()));

        // Revoke
        auditorLinkService.revoke(created.id(), principal);

        // Confirm 404 after revocation
        assertThatThrownBy(() -> auditorLinkService.validateToken(created.token()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Expiry enforcement ────────────────────────────────────────────────

    @Test
    void expiredLink_returnsNotFound() {
        // Create with 1-day expiry, then manually expire it
        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                "Expiry Test", List.of(), true, false, true,
                null, null, 1);
        AuditorLinkDto created = auditorLinkService.create(directoryId, request, principal);

        // Manually set expiry to past
        AuditorLink link = auditorLinkRepo.findById(created.id()).orElseThrow();
        link.setExpiresAt(java.time.OffsetDateTime.now().minusHours(1));
        auditorLinkRepo.save(link);
        em.flush();

        assertThatThrownBy(() -> auditorLinkService.validateToken(created.token()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── HMAC tamper detection ─────────────────────────────────────────────

    @Test
    void hmacTamper_modifyScopeInDb_returnsNotFound() {
        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                "Tamper Test", List.of(), true, false, true,
                null, null, 30);
        AuditorLinkDto created = auditorLinkService.create(directoryId, request, principal);

        // Tamper: change scope in DB without recomputing HMAC
        AuditorLink link = auditorLinkRepo.findById(created.id()).orElseThrow();
        link.setIncludeSod(false); // was true — HMAC now mismatches
        auditorLinkRepo.save(link);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> auditorLinkService.validateToken(created.token()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Rate limiting ─────────────────────────────────────────────────────

    @Test
    void rateLimiter_11thRequest_returns429() {
        String testIp = "192.168.99." + (int)(Math.random() * 250);
        for (int i = 0; i < 10; i++) {
            ipRateLimiter.check(testIp);
        }
        assertThatThrownBy(() -> ipRateLimiter.check(testIp))
                .isInstanceOf(TooManyRequestsException.class);
    }

    // ── Scope enforcement ─────────────────────────────────────────────────

    @Test
    void scopeEnforcement_includeSodFalse_linkReflectsScope() {
        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                "Scope Test", List.of(), false, false, false,
                null, null, 30);
        AuditorLinkDto created = auditorLinkService.create(directoryId, request, principal);

        AuditorLink link = auditorLinkService.validateToken(created.token());
        assertThat(link.isIncludeSod()).isFalse();
        assertThat(link.isIncludeEntitlements()).isFalse();
        assertThat(link.isIncludeAuditEvents()).isFalse();
    }

    // ── Idempotent revocation ─────────────────────────────────────────────

    @Test
    void doubleRevoke_isIdempotent() {
        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                "Double Revoke", List.of(), true, false, true,
                null, null, 30);
        AuditorLinkDto created = auditorLinkService.create(directoryId, request, principal);

        auditorLinkService.revoke(created.id(), principal);
        // Second revoke should not throw
        assertThatCode(() -> auditorLinkService.revoke(created.id(), principal))
                .doesNotThrowAnyException();
    }

    // ── IP logging overload ───────────────────────────────────────────────

    @Test
    void validateTokenWithIp_returnsLink() {
        CreateAuditorLinkRequest request = new CreateAuditorLinkRequest(
                "IP Test", List.of(), true, false, true,
                null, null, 30);
        AuditorLinkDto created = auditorLinkService.create(directoryId, request, principal);

        AuditorLink link = auditorLinkService.validateToken(
                created.token(), "10.0.0.1", "Mozilla/5.0");
        assertThat(link).isNotNull();
        assertThat(link.getAccessCount()).isEqualTo(1);
    }
}
