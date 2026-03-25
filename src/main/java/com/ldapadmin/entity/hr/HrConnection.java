package com.ldapadmin.entity.hr;

import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.HrProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "hr_connections",
       uniqueConstraints = @UniqueConstraint(
               name = "hr_connections_directory_id_provider_key",
               columnNames = {"directory_id", "provider"}))
@Getter
@Setter
@NoArgsConstructor
public class HrConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "directory_id", nullable = false)
    private DirectoryConnection directory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private HrProvider provider = HrProvider.BAMBOOHR;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(length = 200)
    private String subdomain;

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(name = "match_attribute", nullable = false, length = 100)
    private String matchAttribute = "mail";

    @Column(name = "match_field", nullable = false, length = 100)
    private String matchField = "workEmail";

    @Column(name = "sync_cron", nullable = false, length = 50)
    private String syncCron = "0 0 * * * ?";

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column(name = "last_sync_status", length = 20)
    private String lastSyncStatus;

    @Column(name = "last_sync_message", columnDefinition = "TEXT")
    private String lastSyncMessage;

    @Column(name = "last_sync_employee_count")
    private Integer lastSyncEmployeeCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Account createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
