package com.ldapadmin.service;

import com.ldapadmin.dto.superadmin.CreateSuperadminRequest;
import com.ldapadmin.dto.superadmin.ResetPasswordRequest;
import com.ldapadmin.dto.superadmin.SuperadminResponse;
import com.ldapadmin.dto.superadmin.UpdateSuperadminRequest;
import com.ldapadmin.entity.SuperadminAccount;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.SuperadminAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SuperadminManagementService {

    private final SuperadminAccountRepository superadminRepo;
    private final PasswordEncoder             passwordEncoder;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<SuperadminResponse> listSuperadmins() {
        return superadminRepo.findAll().stream()
                .map(SuperadminResponse::from)
                .toList();
    }

    public SuperadminResponse getSuperadmin(UUID id) {
        return SuperadminResponse.from(require(id));
    }

    @Transactional
    public SuperadminResponse createSuperadmin(CreateSuperadminRequest req) {
        if (superadminRepo.findByUsername(req.username()).isPresent()) {
            throw new ConflictException("Superadmin [" + req.username() + "] already exists");
        }
        SuperadminAccount a = new SuperadminAccount();
        a.setUsername(req.username());
        a.setPasswordHash(passwordEncoder.encode(req.password()));
        a.setDisplayName(req.displayName());
        a.setEmail(req.email());
        a.setAccountType(AccountType.LOCAL);
        a.setActive(true);
        return SuperadminResponse.from(superadminRepo.save(a));
    }

    @Transactional
    public SuperadminResponse updateSuperadmin(UUID id, UpdateSuperadminRequest req) {
        SuperadminAccount a = require(id);
        a.setDisplayName(req.displayName());
        a.setEmail(req.email());
        // Guard: cannot deactivate last LOCAL superadmin
        if (!req.active() && a.isActive()
                && a.getAccountType() == AccountType.LOCAL
                && superadminRepo.countByAccountTypeAndActiveTrueAndIdNot(AccountType.LOCAL, id) == 0) {
            throw new IllegalArgumentException(
                    "Cannot deactivate the last active LOCAL superadmin");
        }
        a.setActive(req.active());
        return SuperadminResponse.from(superadminRepo.save(a));
    }

    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest req) {
        SuperadminAccount a = require(id);
        if (a.getAccountType() != AccountType.LOCAL) {
            throw new IllegalArgumentException(
                    "Password reset is only supported for LOCAL superadmin accounts");
        }
        a.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        superadminRepo.save(a);
    }

    @Transactional
    public void deleteSuperadmin(UUID id) {
        SuperadminAccount a = require(id);
        // Guard: never delete the last active LOCAL superadmin
        if (a.getAccountType() == AccountType.LOCAL && a.isActive()
                && superadminRepo.countByAccountTypeAndActiveTrueAndIdNot(AccountType.LOCAL, id) == 0) {
            throw new IllegalArgumentException(
                    "Cannot delete the last active LOCAL superadmin");
        }
        superadminRepo.delete(a);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private SuperadminAccount require(UUID id) {
        return superadminRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SuperadminAccount", id));
    }
}
