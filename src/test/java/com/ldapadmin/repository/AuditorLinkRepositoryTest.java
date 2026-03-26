package com.ldapadmin.repository;

import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.AuditorLink;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AccountRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AuditorLinkRepository}.
 * Uses H2 in PostgreSQL mode with Hibernate auto-DDL (no Flyway).
 * Verifies query derivation, JSONB round-trip for {@code List<UUID>}, and
 * revoked-link exclusion.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditorLinkRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private AuditorLinkRepository repo;

    private DirectoryConnection directory;
    private Account account;

    @BeforeEach
    void setUp() {
        directory = new DirectoryConnection();
        directory.setDisplayName("Test LDAP");
        directory.setHost("localhost");
        directory.setPort(389);
        directory.setBindDn("cn=admin");
        directory.setBindPasswordEncrypted("encrypted");
        directory.setBaseDn("dc=example,dc=com");
        em.persist(directory);

        account = new Account();
        account.setUsername("auditor-test-" + UUID.randomUUID());
        account.setPasswordHash("hash");
        account.setRole(AccountRole.SUPERADMIN);
        em.persist(account);

        em.flush();
    }

    @Test
    void save_andFindByToken_roundTripsAllFields() {
        UUID campaign1 = UUID.randomUUID();
        UUID campaign2 = UUID.randomUUID();

        AuditorLink link = AuditorLink.builder()
                .directory(directory)
                .token("test-token-" + UUID.randomUUID())
                .label("Q1 2026 SOC 2 Audit")
                .campaignIds(List.of(campaign1, campaign2))
                .includeSod(true)
                .includeEntitlements(false)
                .includeAuditEvents(true)
                .dataFrom(OffsetDateTime.now().minusDays(90))
                .dataTo(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .hmacSignature("abc123hmac")
                .createdBy(account)
                .build();

        em.persist(link);
        em.flush();
        em.clear(); // Force reload from DB

        Optional<AuditorLink> found = repo.findByTokenAndRevokedFalse(link.getToken());

        assertThat(found).isPresent();
        AuditorLink loaded = found.get();
        assertThat(loaded.getLabel()).isEqualTo("Q1 2026 SOC 2 Audit");
        assertThat(loaded.getCampaignIds()).containsExactly(campaign1, campaign2);
        assertThat(loaded.isIncludeSod()).isTrue();
        assertThat(loaded.isIncludeEntitlements()).isFalse();
        assertThat(loaded.isIncludeAuditEvents()).isTrue();
        assertThat(loaded.getHmacSignature()).isEqualTo("abc123hmac");
        assertThat(loaded.getAccessCount()).isZero();
        assertThat(loaded.isRevoked()).isFalse();
    }

    @Test
    void findByTokenAndRevokedFalse_excludesRevokedLinks() {
        String token = "revoked-" + UUID.randomUUID();
        AuditorLink link = buildLink(token);
        link.setRevoked(true);
        link.setRevokedAt(OffsetDateTime.now());
        em.persist(link);
        em.flush();

        Optional<AuditorLink> found = repo.findByTokenAndRevokedFalse(token);

        assertThat(found).isEmpty();
    }

    @Test
    void findByDirectoryIdOrderByCreatedAtDesc_returnsLinksForDirectory() {
        AuditorLink link1 = buildLink("link1-" + UUID.randomUUID());
        em.persist(link1);
        AuditorLink link2 = buildLink("link2-" + UUID.randomUUID());
        em.persist(link2);
        em.flush();

        List<AuditorLink> links = repo.findByDirectoryIdOrderByCreatedAtDesc(directory.getId());

        assertThat(links).hasSize(2);
    }

    @Test
    void findByDirectoryIdOrderByCreatedAtDesc_excludesOtherDirectories() {
        AuditorLink link = buildLink("my-token-" + UUID.randomUUID());
        em.persist(link);
        em.flush();

        UUID otherDirectoryId = UUID.randomUUID();
        List<AuditorLink> links = repo.findByDirectoryIdOrderByCreatedAtDesc(otherDirectoryId);

        assertThat(links).isEmpty();
    }

    @Test
    void campaignIds_emptyList_roundTripsCorrectly() {
        String token = "empty-campaigns-" + UUID.randomUUID();
        AuditorLink link = buildLink(token);
        em.persist(link);
        em.flush();
        em.clear();

        AuditorLink loaded = repo.findByTokenAndRevokedFalse(token).orElseThrow();

        assertThat(loaded.getCampaignIds()).isEmpty();
    }

    private AuditorLink buildLink(String token) {
        return AuditorLink.builder()
                .directory(directory)
                .token(token)
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .hmacSignature("sig-" + token)
                .createdBy(account)
                .build();
    }
}
