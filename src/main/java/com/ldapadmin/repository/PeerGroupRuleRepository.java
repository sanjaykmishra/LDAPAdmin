package com.ldapadmin.repository;

import com.ldapadmin.entity.PeerGroupRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PeerGroupRuleRepository extends JpaRepository<PeerGroupRule, UUID> {

    List<PeerGroupRule> findByDirectoryIdOrderByCreatedAtDesc(UUID directoryId);

    List<PeerGroupRule> findByDirectoryIdAndEnabledTrue(UUID directoryId);
}
