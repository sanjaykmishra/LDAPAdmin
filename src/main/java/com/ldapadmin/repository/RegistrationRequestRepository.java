package com.ldapadmin.repository;

import com.ldapadmin.entity.RegistrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, UUID> {

    Optional<RegistrationRequest> findByVerificationToken(String token);

    Optional<RegistrationRequest> findByIdAndEmail(UUID id, String email);

    Optional<RegistrationRequest> findByPendingApprovalId(UUID pendingApprovalId);
}
