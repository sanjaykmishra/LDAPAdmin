package com.ldapadmin.service;

import com.ldapadmin.dto.superadmin.CreateSuperadminRequest;
import com.ldapadmin.dto.superadmin.ResetPasswordRequest;
import com.ldapadmin.dto.superadmin.UpdateSuperadminRequest;
import com.ldapadmin.entity.SuperadminAccount;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.SuperadminAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperadminManagementServiceTest {

    @Mock private SuperadminAccountRepository repo;

    private PasswordEncoder              encoder = new BCryptPasswordEncoder();
    private SuperadminManagementService  service;

    @BeforeEach
    void setUp() {
        service = new SuperadminManagementService(repo, encoder);
    }

    @Test
    void createSuperadmin_encodesPassword() {
        when(repo.findByUsername("alice")).thenReturn(Optional.empty());
        when(repo.save(any(SuperadminAccount.class))).thenAnswer(inv -> {
            SuperadminAccount a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        service.createSuperadmin(new CreateSuperadminRequest("alice", "p@ssw0rd!", null, null));

        ArgumentCaptor<SuperadminAccount> captor = ArgumentCaptor.forClass(SuperadminAccount.class);
        verify(repo).save(captor.capture());
        assertThat(encoder.matches("p@ssw0rd!", captor.getValue().getPasswordHash())).isTrue();
        assertThat(captor.getValue().getAccountType()).isEqualTo(AccountType.LOCAL);
    }

    @Test
    void createSuperadmin_duplicateUsername_throwsConflict() {
        SuperadminAccount existing = new SuperadminAccount();
        when(repo.findByUsername("alice")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                service.createSuperadmin(new CreateSuperadminRequest("alice", "pass1234", null, null)))
                .isInstanceOf(ConflictException.class);

        verify(repo, never()).save(any());
    }

    @Test
    void deleteSuperadmin_lastLocalActive_throws() {
        UUID id = UUID.randomUUID();
        SuperadminAccount a = localAccount(id, true);
        when(repo.findById(id)).thenReturn(Optional.of(a));
        when(repo.countByAccountTypeAndActiveTrueAndIdNot(AccountType.LOCAL, id)).thenReturn(0L);

        assertThatThrownBy(() -> service.deleteSuperadmin(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last active LOCAL superadmin");
    }

    @Test
    void resetPassword_ldapAccount_throws() {
        UUID id = UUID.randomUUID();
        SuperadminAccount a = new SuperadminAccount();
        a.setId(id);
        a.setAccountType(AccountType.LDAP);
        when(repo.findById(id)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.resetPassword(id, new ResetPasswordRequest("newpass1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getSuperadmin_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSuperadmin(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SuperadminAccount localAccount(UUID id, boolean active) {
        SuperadminAccount a = new SuperadminAccount();
        a.setId(id);
        a.setUsername("admin");
        a.setAccountType(AccountType.LOCAL);
        a.setPasswordHash(encoder.encode("secret"));
        a.setActive(active);
        return a;
    }
}
