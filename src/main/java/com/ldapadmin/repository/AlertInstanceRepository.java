package com.ldapadmin.repository;

import com.ldapadmin.entity.AlertInstance;
import com.ldapadmin.entity.enums.AlertSeverity;
import com.ldapadmin.entity.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AlertInstanceRepository extends JpaRepository<AlertInstance, UUID> {

    Page<AlertInstance> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AlertInstance> findAllByStatusOrderByCreatedAtDesc(AlertStatus status, Pageable pageable);

    Page<AlertInstance> findAllByDirectoryIdOrderByCreatedAtDesc(UUID directoryId, Pageable pageable);

    Page<AlertInstance> findAllByDirectoryIdAndStatusOrderByCreatedAtDesc(
            UUID directoryId, AlertStatus status, Pageable pageable);

    long countByStatus(AlertStatus status);

    long countByStatusAndSeverity(AlertStatus status, AlertSeverity severity);

    long countByDirectoryIdAndStatus(UUID directoryId, AlertStatus status);

    /** Check for existing open/acknowledged alert with same rule + context key (deduplication). */
    boolean existsByRuleIdAndContextKeyAndStatusIn(UUID ruleId, String contextKey, List<AlertStatus> statuses);

    /** Find the most recent instance for a rule + context key (for cooldown check). */
    @Query("SELECT a FROM AlertInstance a WHERE a.rule.id = :ruleId AND a.contextKey = :contextKey " +
           "ORDER BY a.createdAt DESC LIMIT 1")
    AlertInstance findLatestByRuleIdAndContextKey(@Param("ruleId") UUID ruleId,
                                                  @Param("contextKey") String contextKey);
}
