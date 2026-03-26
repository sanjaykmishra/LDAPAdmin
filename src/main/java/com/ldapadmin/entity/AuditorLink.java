package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A shareable, time-scoped, read-only link that grants an external auditor
 * access to a scoped evidence package without needing an LDAPAdmin account.
 *
 * <p>The {@link #token} is a 256-bit cryptographically random value
 * (Base64URL-encoded) and serves as the sole credential. The
 * {@link #hmacSignature} covers the token, scope, and expiry to prevent
 * tampering.</p>
 */
@Entity
@Table(name = "auditor_links")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditorLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id", nullable = false)
    private DirectoryConnection directory;

    /** Base64URL-encoded 256-bit random token — the sole credential. */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /** Human-readable label, e.g. "Q1 2026 SOC 2 Audit — Deloitte". */
    @Column(length = 255)
    private String label;

    // ── Scope ──────────────────────────────────────────────────────────────────

    /** UUIDs of the campaigns the auditor can see. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "campaign_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> campaignIds = List.of();

    @Column(name = "include_sod", nullable = false)
    @Builder.Default
    private boolean includeSod = true;

    @Column(name = "include_entitlements", nullable = false)
    @Builder.Default
    private boolean includeEntitlements = false;

    @Column(name = "include_audit_events", nullable = false)
    @Builder.Default
    private boolean includeAuditEvents = true;

    // ── Time bounds ────────────────────────────────────────────────────────────

    /** Evidence window start (nullable — no lower bound). */
    @Column(name = "data_from")
    private OffsetDateTime dataFrom;

    /** Evidence window end (nullable — no upper bound). */
    @Column(name = "data_to")
    private OffsetDateTime dataTo;

    /** When the link itself expires and can no longer be accessed. */
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    // ── Signature ──────────────────────────────────────────────────────────────

    /** HMAC-SHA256 over (token + directoryId + scope + expiresAt). */
    @Column(name = "hmac_signature", nullable = false, length = 128)
    private String hmacSignature;

    // ── Tracking ───────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Account createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_accessed_at")
    private OffsetDateTime lastAccessedAt;

    @Column(name = "access_count", nullable = false)
    @Builder.Default
    private int accessCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    // ── Helpers ─────────────────────────────────────────────────────────────────

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    public boolean isAccessible() {
        return !revoked && !isExpired();
    }
}
