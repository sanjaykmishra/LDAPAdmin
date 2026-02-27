package com.ldapadmin.service;

import com.ldapadmin.dto.settings.ApplicationSettingsDto;
import com.ldapadmin.dto.settings.UpdateApplicationSettingsRequest;
import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.repository.ApplicationSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationSettingsServiceTest {

    @Mock private ApplicationSettingsRepository settingsRepo;
    @Mock private EncryptionService             encryptionService;

    private ApplicationSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationSettingsService(settingsRepo, encryptionService);
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_existingSettings_returnsDto() {
        ApplicationSettings settings = existingSettings();
        when(settingsRepo.findFirstBy()).thenReturn(Optional.of(settings));

        ApplicationSettingsDto dto = service.get();

        assertThat(dto.appName()).isEqualTo("My App");
        assertThat(dto.sessionTimeoutMinutes()).isEqualTo(30);
        assertThat(dto.smtpPasswordConfigured()).isTrue();
        assertThat(dto.s3SecretKeyConfigured()).isFalse();
    }

    @Test
    void get_noSettingsExist_returnsDefaults() {
        when(settingsRepo.findFirstBy()).thenReturn(Optional.empty());

        ApplicationSettingsDto dto = service.get();

        assertThat(dto.id()).isNull();
        assertThat(dto.appName()).isEqualTo("LDAP Portal");
        assertThat(dto.sessionTimeoutMinutes()).isEqualTo(60);
        assertThat(dto.smtpPasswordConfigured()).isFalse();
    }

    // ── upsert (create) ───────────────────────────────────────────────────────

    @Test
    void upsert_noExistingSettings_createsNew() {
        when(settingsRepo.findFirstBy()).thenReturn(Optional.empty());
        when(encryptionService.encrypt("secret123")).thenReturn("enc-secret");
        when(settingsRepo.save(any(ApplicationSettings.class)))
                .thenAnswer(inv -> {
                    ApplicationSettings s = inv.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });

        UpdateApplicationSettingsRequest req = new UpdateApplicationSettingsRequest(
                "New App", null, null, null, 45,
                "smtp.test.com", 587, null, null, "secret123", false,
                null, null, null, null, null, 24);

        ApplicationSettingsDto dto = service.upsert(req);

        assertThat(dto.appName()).isEqualTo("New App");
        ArgumentCaptor<ApplicationSettings> captor =
                ArgumentCaptor.forClass(ApplicationSettings.class);
        verify(settingsRepo).save(captor.capture());
        assertThat(captor.getValue().getSmtpPasswordEncrypted()).isEqualTo("enc-secret");
        assertThat(captor.getValue().getSessionTimeoutMinutes()).isEqualTo(45);
    }

    @Test
    void upsert_existingSettings_updatesRow() {
        ApplicationSettings existing = existingSettings();
        when(settingsRepo.findFirstBy()).thenReturn(Optional.of(existing));
        when(settingsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApplicationSettingsDto dto = service.upsert(basicRequest());

        assertThat(dto.appName()).isEqualTo("My App");
        // password was null in request → preserved
        ArgumentCaptor<ApplicationSettings> captor =
                ArgumentCaptor.forClass(ApplicationSettings.class);
        verify(settingsRepo).save(captor.capture());
        assertThat(captor.getValue().getSmtpPasswordEncrypted()).isEqualTo("encrypted-pw");
        verify(encryptionService, never()).encrypt(any());
    }

    // ── Password handling ─────────────────────────────────────────────────────

    @Test
    void upsert_emptyPassword_clearsEncryptedValue() {
        ApplicationSettings existing = existingSettings();
        when(settingsRepo.findFirstBy()).thenReturn(Optional.of(existing));
        when(settingsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateApplicationSettingsRequest req = new UpdateApplicationSettingsRequest(
                "My App", null, null, null, 30,
                null, 587, null, null, "", false,  // empty string → clear
                null, null, null, null, null, 24);

        service.upsert(req);

        ArgumentCaptor<ApplicationSettings> captor =
                ArgumentCaptor.forClass(ApplicationSettings.class);
        verify(settingsRepo).save(captor.capture());
        assertThat(captor.getValue().getSmtpPasswordEncrypted()).isNull();
    }

    @Test
    void upsert_nonEmptyPassword_encryptsAndStores() {
        ApplicationSettings existing = existingSettings();
        when(settingsRepo.findFirstBy()).thenReturn(Optional.of(existing));
        when(encryptionService.encrypt("newpass")).thenReturn("enc-newpass");
        when(settingsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateApplicationSettingsRequest req = new UpdateApplicationSettingsRequest(
                "My App", null, null, null, 30,
                null, 587, null, null, "newpass", false,
                null, null, null, null, null, 24);

        service.upsert(req);

        ArgumentCaptor<ApplicationSettings> captor =
                ArgumentCaptor.forClass(ApplicationSettings.class);
        verify(settingsRepo).save(captor.capture());
        assertThat(captor.getValue().getSmtpPasswordEncrypted()).isEqualTo("enc-newpass");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ApplicationSettings existingSettings() {
        ApplicationSettings s = new ApplicationSettings();
        s.setId(UUID.randomUUID());
        s.setAppName("My App");
        s.setSessionTimeoutMinutes(30);
        s.setSmtpHost("smtp.example.com");
        s.setSmtpPasswordEncrypted("encrypted-pw");
        s.setS3SecretKeyEncrypted(null);
        return s;
    }

    private UpdateApplicationSettingsRequest basicRequest() {
        return new UpdateApplicationSettingsRequest(
                "My App", null, "#fff", null,
                30,
                "smtp.example.com", 587, "noreply@example.com", "user",
                null,  // keep existing password
                true,
                null, null, null, null, null, 24);
    }
}
