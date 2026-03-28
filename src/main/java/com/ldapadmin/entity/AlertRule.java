package com.ldapadmin.entity;

import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.entity.enums.AlertSeverity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "directory_id")
    private DirectoryConnection directory;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 80)
    private AlertRuleType ruleType;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity = AlertSeverity.MEDIUM;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> params = Map.of();

    @Column(name = "notify_in_app", nullable = false)
    private boolean notifyInApp = true;

    @Column(name = "notify_email", nullable = false)
    private boolean notifyEmail = false;

    @Column(name = "email_recipients")
    private String emailRecipients;

    @Column(name = "cooldown_hours", nullable = false)
    private int cooldownHours = 24;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
