package com.ldapadmin.controller.superadmin;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.alert.*;
import com.ldapadmin.entity.enums.AlertSeverity;
import com.ldapadmin.entity.enums.AlertStatus;
import com.ldapadmin.service.alerting.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/superadmin/alerts")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public Page<AlertInstanceResponse> list(
            @RequestParam(required = false) UUID directoryId,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return alertService.listInstances(directoryId, status, severity, page, size);
    }

    @GetMapping("/summary")
    public AlertSummaryResponse summary() {
        return alertService.getSummary();
    }

    @PostMapping("/{id}/acknowledge")
    public void acknowledge(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal principal) {
        alertService.acknowledge(id, principal.id());
    }

    @PostMapping("/{id}/dismiss")
    public void dismiss(@PathVariable UUID id) {
        alertService.dismiss(id);
    }

    @PostMapping("/{id}/resolve")
    public void resolve(@PathVariable UUID id) {
        alertService.resolve(id);
    }

    // ── Rules ────────────────────────────────────────────────────────────────

    @GetMapping("/rules")
    public List<AlertRuleResponse> listRules(@RequestParam(required = false) UUID directoryId) {
        return alertService.listRules(directoryId);
    }

    @PutMapping("/rules/{id}")
    public AlertRuleResponse updateRule(@PathVariable UUID id, @RequestBody UpdateAlertRuleRequest request) {
        return alertService.updateRule(id, request);
    }

    @PostMapping("/rules/initialize/{directoryId}")
    public List<AlertRuleResponse> initializeDefaults(@PathVariable UUID directoryId) {
        return alertService.initializeDefaults(directoryId);
    }
}
