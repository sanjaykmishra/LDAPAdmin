package com.ldapadmin.repository;

import com.ldapadmin.entity.UserTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserTemplateRepository extends JpaRepository<UserTemplate, UUID> {

    List<UserTemplate> findAllByObjectClassNamesContaining(String objectClassName);
}
