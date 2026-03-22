package com.ldapadmin.repository;

import com.ldapadmin.entity.UserTemplateAttributeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserTemplateAttributeConfigRepository extends JpaRepository<UserTemplateAttributeConfig, UUID> {

    List<UserTemplateAttributeConfig> findAllByUserTemplateIdOrderByDisplayOrderAsc(UUID userTemplateId);

    void deleteAllByUserTemplateId(UUID userTemplateId);
}
