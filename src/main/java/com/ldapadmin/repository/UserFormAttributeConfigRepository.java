package com.ldapadmin.repository;

import com.ldapadmin.entity.UserFormAttributeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserFormAttributeConfigRepository extends JpaRepository<UserFormAttributeConfig, UUID> {

    List<UserFormAttributeConfig> findAllByUserFormIdOrderByDisplayOrderAsc(UUID userFormId);

    void deleteAllByUserFormId(UUID userFormId);
}
