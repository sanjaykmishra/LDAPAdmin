package com.ldapadmin.service;

import com.ldapadmin.config.AppProperties;
import com.ldapadmin.entity.SuperadminAccount;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.repository.SuperadminAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BootstrapServiceTest {

    @Mock
    private SuperadminAccountRepository superadminRepo;

    private AppProperties   appProperties;
    private PasswordEncoder passwordEncoder;
    private BootstrapService bootstrapService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getBootstrap().getSuperadmin().setUsername("superadmin");
        appProperties.getBootstrap().getSuperadmin().setPassword("s3cr3t!");

        passwordEncoder  = new BCryptPasswordEncoder();
        bootstrapService = new BootstrapService(superadminRepo, appProperties, passwordEncoder);
    }

    @Test
    void createsAccount_whenNoLocalSuperadminExists() throws Exception {
        when(superadminRepo.countByAccountTypeAndActiveTrue(AccountType.LOCAL)).thenReturn(0L);

        bootstrapService.run(new DefaultApplicationArguments());

        ArgumentCaptor<SuperadminAccount> captor = ArgumentCaptor.forClass(SuperadminAccount.class);
        verify(superadminRepo).save(captor.capture());

        SuperadminAccount saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("superadmin");
        assertThat(saved.getAccountType()).isEqualTo(AccountType.LOCAL);
        assertThat(saved.isActive()).isTrue();
        assertThat(passwordEncoder.matches("s3cr3t!", saved.getPasswordHash())).isTrue();
    }

    @Test
    void skipsBootstrap_whenLocalSuperadminAlreadyExists() throws Exception {
        when(superadminRepo.countByAccountTypeAndActiveTrue(AccountType.LOCAL)).thenReturn(1L);

        bootstrapService.run(new DefaultApplicationArguments());

        verify(superadminRepo, never()).save(any());
    }
}
