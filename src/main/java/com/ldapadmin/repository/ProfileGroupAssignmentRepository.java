package com.ldapadmin.repository;

import com.ldapadmin.entity.ProfileGroupAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileGroupAssignmentRepository extends JpaRepository<ProfileGroupAssignment, UUID> {

    List<ProfileGroupAssignment> findAllByProfileIdOrderByDisplayOrderAsc(UUID profileId);

    void deleteAllByProfileId(UUID profileId);
}
