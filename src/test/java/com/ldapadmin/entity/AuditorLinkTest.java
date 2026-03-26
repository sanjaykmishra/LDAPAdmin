package com.ldapadmin.entity;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AuditorLink} entity helper methods and builder defaults.
 */
class AuditorLinkTest {

    @Test
    void isExpired_returnsFalse_whenExpiresAtInFuture() {
        AuditorLink link = AuditorLink.builder()
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .build();

        assertThat(link.isExpired()).isFalse();
    }

    @Test
    void isExpired_returnsTrue_whenExpiresAtInPast() {
        AuditorLink link = AuditorLink.builder()
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .build();

        assertThat(link.isExpired()).isTrue();
    }

    @Test
    void isAccessible_returnsTrue_whenNotRevokedAndNotExpired() {
        AuditorLink link = AuditorLink.builder()
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .revoked(false)
                .build();

        assertThat(link.isAccessible()).isTrue();
    }

    @Test
    void isAccessible_returnsFalse_whenRevoked() {
        AuditorLink link = AuditorLink.builder()
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .revoked(true)
                .build();

        assertThat(link.isAccessible()).isFalse();
    }

    @Test
    void isAccessible_returnsFalse_whenExpired() {
        AuditorLink link = AuditorLink.builder()
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .revoked(false)
                .build();

        assertThat(link.isAccessible()).isFalse();
    }

    @Test
    void builder_defaults_areCorrect() {
        AuditorLink link = AuditorLink.builder()
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .build();

        assertThat(link.getCampaignIds()).isEmpty();
        assertThat(link.isIncludeSod()).isTrue();
        assertThat(link.isIncludeEntitlements()).isFalse();
        assertThat(link.isIncludeAuditEvents()).isTrue();
        assertThat(link.getAccessCount()).isZero();
        assertThat(link.isRevoked()).isFalse();
    }

    @Test
    void builder_setCampaignIds_storesList() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        AuditorLink link = AuditorLink.builder()
                .campaignIds(List.of(id1, id2))
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .build();

        assertThat(link.getCampaignIds()).containsExactly(id1, id2);
    }
}
