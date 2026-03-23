package com.ldapadmin.repository;

import com.ldapadmin.entity.PlaybookExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlaybookExecutionRepository extends JpaRepository<PlaybookExecution, UUID> {

    List<PlaybookExecution> findAllByPlaybookIdOrderByStartedAtDesc(UUID playbookId);

    List<PlaybookExecution> findTop20ByPlaybookIdOrderByStartedAtDesc(UUID playbookId);
}
