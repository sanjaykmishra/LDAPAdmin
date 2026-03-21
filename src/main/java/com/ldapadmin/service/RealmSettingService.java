package com.ldapadmin.service;

import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.RealmSetting;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.RealmRepository;
import com.ldapadmin.repository.RealmSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RealmSettingService {

    static final String KEY_APPROVAL_ENABLED = "approval.user_create.enabled";
    static final String KEY_APPROVER_GROUP_DN = "approval.approver_group_dn";

    private final RealmSettingRepository settingRepo;
    private final RealmRepository realmRepo;

    @Transactional(readOnly = true)
    public Optional<String> getSetting(UUID realmId, String key) {
        return settingRepo.findByRealmIdAndKey(realmId, key)
                .map(RealmSetting::getValue);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAllSettings(UUID realmId) {
        return settingRepo.findAllByRealmId(realmId).stream()
                .collect(Collectors.toMap(RealmSetting::getKey, RealmSetting::getValue));
    }

    @Transactional
    public void setSetting(UUID realmId, String key, String value) {
        Realm realm = realmRepo.findById(realmId)
                .orElseThrow(() -> new ResourceNotFoundException("Realm", realmId));

        RealmSetting setting = settingRepo.findByRealmIdAndKey(realmId, key)
                .orElseGet(() -> {
                    RealmSetting s = new RealmSetting();
                    s.setRealm(realm);
                    s.setKey(key);
                    return s;
                });
        setting.setValue(value);
        settingRepo.save(setting);
    }

    @Transactional
    public void setSettings(UUID realmId, Map<String, String> settings) {
        settings.forEach((key, value) -> setSetting(realmId, key, value));
    }

    @Transactional(readOnly = true)
    public boolean isApprovalRequired(UUID realmId) {
        return getSetting(realmId, KEY_APPROVAL_ENABLED)
                .map("true"::equals)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<String> getApproverGroupDn(UUID realmId) {
        return getSetting(realmId, KEY_APPROVER_GROUP_DN)
                .filter(s -> !s.isBlank());
    }
}
