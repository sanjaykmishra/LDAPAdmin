package com.ldapadmin.repository;

import com.ldapadmin.entity.UserForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserFormRepository extends JpaRepository<UserForm, UUID> {

    List<UserForm> findAllByObjectClassName(String objectClassName);
}
