package com.ldapadmin.controller.directory;

import com.ldapadmin.dto.approval.RealmApproverResponse;
import com.ldapadmin.dto.approval.RealmSettingsRequest;
import com.ldapadmin.dto.approval.RealmSettingsResponse;
import com.ldapadmin.dto.approval.SetApproversRequest;
import com.ldapadmin.entity.Account;
import com.ldapadmin.service.RealmApproverService;
import com.ldapadmin.service.RealmSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directories/{directoryId}/realms/{realmId}")
@RequiredArgsConstructor
public class RealmSettingsController {

    private final RealmSettingService settingService;
    private final RealmApproverService approverService;

    // ── Realm Settings ────────────────────────────────────────────────────────

    @GetMapping("/settings")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public RealmSettingsResponse getSettings(@PathVariable UUID realmId) {
        return new RealmSettingsResponse(settingService.getAllSettings(realmId));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public RealmSettingsResponse updateSettings(
            @PathVariable UUID realmId,
            @Valid @RequestBody RealmSettingsRequest req) {
        settingService.setSettings(realmId, req.settings());
        return new RealmSettingsResponse(settingService.getAllSettings(realmId));
    }

    // ── Approver Management ───────────────────────────────────────────────────

    @GetMapping("/approvers")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<RealmApproverResponse> getApprovers(@PathVariable UUID realmId) {
        return approverService.getApprovers(realmId).stream()
                .map(a -> new RealmApproverResponse(a.getId(), a.getUsername(), a.getEmail()))
                .toList();
    }

    @PutMapping("/approvers")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> setApprovers(
            @PathVariable UUID realmId,
            @Valid @RequestBody SetApproversRequest req) {
        if (approverService.isLdapAuthEnabled()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "Approvers are managed via LDAP group membership when LDAP auth is enabled. "
                            + "Configure the approver group DN in realm settings instead."));
        }
        approverService.setApprovers(realmId, req.accountIds());
        return ResponseEntity.ok(approverService.getApprovers(realmId).stream()
                .map(a -> new RealmApproverResponse(a.getId(), a.getUsername(), a.getEmail()))
                .toList());
    }

    @GetMapping("/approval-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public Map<String, Object> getApprovalConfig(@PathVariable UUID realmId) {
        return Map.of(
                "approvalRequired", settingService.isApprovalRequired(realmId),
                "ldapAuthEnabled", approverService.isLdapAuthEnabled());
    }
}
