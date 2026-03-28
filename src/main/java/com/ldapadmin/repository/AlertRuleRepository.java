package com.ldapadmin.repository;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.enums.AlertRuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    List<AlertRule> findAllByEnabledTrue();

    List<AlertRule> findAllByDirectoryIdOrderByRuleTypeAsc(UUID directoryId);

    @Query("SELECT r FROM AlertRule r ORDER BY r.directory.id ASC, r.ruleType ASC")
    List<AlertRule> findAllOrderedByDirectoryAndType();

    Optional<AlertRule> findByDirectoryIdAndRuleType(UUID directoryId, AlertRuleType ruleType);

    boolean existsByDirectoryId(UUID directoryId);
}
