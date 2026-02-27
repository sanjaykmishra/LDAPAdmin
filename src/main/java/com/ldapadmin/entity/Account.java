package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.AccountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Unified account entity for all application users (superadmins and admins).
 * Maps to the {@code accounts} table introduced in V14.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "display_name")
    private String displayName;

    @Column
    private String email;

    /** Application-level role: SUPERADMIN or ADMIN. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    /**
     * Authentication mechanism: LOCAL (bcrypt password) or LDAP (bind against
     * the LDAP auth server configured in application_settings).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 10)
    private AccountType authType = AccountType.LOCAL;

    /** bcrypt hash; NULL for LDAP accounts or accounts pending first-login setup. */
    @Column(name = "password_hash")
    private String passwordHash;

    /** Distinguished name in the LDAP directory (LDAP auth_type only). */
    @Column(name = "ldap_dn", length = 1000)
    private String ldapDn;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
