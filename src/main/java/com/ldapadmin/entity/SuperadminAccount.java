package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.AccountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "superadmin_accounts")
@Getter
@Setter
@NoArgsConstructor
public class SuperadminAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "display_name")
    private String displayName;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    private AccountType accountType = AccountType.LOCAL;

    /**
     * bcrypt-hashed password. NULL for LDAP-sourced accounts.
     * Never logged or exposed in API responses.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Reference to the DirectoryConnection used as the LDAP authentication
     * source for this superadmin. NULL for LOCAL accounts.
     * FK constraint was added in V3 after directory_connections was created.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ldap_source_directory_id")
    private DirectoryConnection ldapSourceDirectory;

    /** Distinguished name in the source LDAP directory. NULL for LOCAL accounts. */
    @Column(name = "ldap_dn")
    private String ldapDn;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
