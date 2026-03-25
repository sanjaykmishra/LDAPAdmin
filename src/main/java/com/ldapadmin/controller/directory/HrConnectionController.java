package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.hr.*;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.entity.enums.HrEmployeeStatus;
import com.ldapadmin.entity.enums.HrSyncTrigger;
import com.ldapadmin.entity.hr.HrConnection;
import com.ldapadmin.entity.hr.HrSyncRun;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.hr.HrConnectionRepository;
import com.ldapadmin.repository.hr.HrEmployeeRepository;
import com.ldapadmin.repository.hr.HrSyncRunRepository;
import com.ldapadmin.service.EncryptionService;
import com.ldapadmin.service.hr.BambooHrClient;
import com.ldapadmin.service.hr.HrSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directories/{directoryId}/hr")
@RequiredArgsConstructor
public class HrConnectionController {

    private final HrConnectionRepository connectionRepo;
    private final HrEmployeeRepository employeeRepo;
    private final HrSyncRunRepository syncRunRepo;
    private final DirectoryConnectionRepository directoryRepo;
    private final AccountRepository accountRepo;
    private final EncryptionService encryptionService;
    private final BambooHrClient bambooHrClient;
    private final HrSyncService syncService;

    @GetMapping
    @RequiresFeature(FeatureKey.HR_VIEW)
    public ResponseEntity<HrConnectionDto> getConnection(
            @DirectoryId @PathVariable UUID directoryId) {
        HrConnection conn = connectionRepo.findByDirectoryId(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No HR connection configured for this directory"));
        return ResponseEntity.ok(HrConnectionDto.from(conn));
    }

    @PostMapping
    @RequiresFeature(FeatureKey.HR_MANAGE)
    public ResponseEntity<HrConnectionDto> createConnection(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateHrConnectionRequest req) {
        DirectoryConnection directory = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Directory not found"));

        HrConnection conn = new HrConnection();
        conn.setDirectory(directory);
        conn.setDisplayName(req.displayName());
        conn.setSubdomain(req.subdomain());
        conn.setApiKeyEncrypted(encryptionService.encrypt(req.apiKey()));
        if (req.matchAttribute() != null) conn.setMatchAttribute(req.matchAttribute());
        if (req.matchField() != null) conn.setMatchField(req.matchField());
        if (req.syncCron() != null) {
            validateCron(req.syncCron());
            conn.setSyncCron(req.syncCron());
        }

        Account creator = accountRepo.findById(principal.id()).orElse(null);
        conn.setCreatedBy(creator);

        connectionRepo.save(conn);
        return ResponseEntity.status(HttpStatus.CREATED).body(HrConnectionDto.from(conn));
    }

    @PutMapping
    @RequiresFeature(FeatureKey.HR_MANAGE)
    public ResponseEntity<HrConnectionDto> updateConnection(
            @DirectoryId @PathVariable UUID directoryId,
            @RequestBody UpdateHrConnectionRequest req) {
        HrConnection conn = connectionRepo.findByDirectoryId(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No HR connection configured for this directory"));

        if (req.displayName() != null) conn.setDisplayName(req.displayName());
        if (req.subdomain() != null) conn.setSubdomain(req.subdomain());
        if (req.apiKey() != null && !req.apiKey().isBlank()) {
            conn.setApiKeyEncrypted(encryptionService.encrypt(req.apiKey()));
        }
        if (req.matchAttribute() != null) conn.setMatchAttribute(req.matchAttribute());
        if (req.matchField() != null) conn.setMatchField(req.matchField());
        if (req.syncCron() != null) {
            validateCron(req.syncCron());
            conn.setSyncCron(req.syncCron());
        }
        if (req.enabled() != null) conn.setEnabled(req.enabled());

        connectionRepo.save(conn);
        return ResponseEntity.ok(HrConnectionDto.from(conn));
    }

    @DeleteMapping
    @RequiresFeature(FeatureKey.HR_MANAGE)
    public ResponseEntity<Void> deleteConnection(
            @DirectoryId @PathVariable UUID directoryId) {
        HrConnection conn = connectionRepo.findByDirectoryId(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No HR connection configured for this directory"));
        connectionRepo.delete(conn);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    @RequiresFeature(FeatureKey.HR_MANAGE)
    public ResponseEntity<HrTestConnectionResponse> testConnection(
            @DirectoryId @PathVariable UUID directoryId,
            @Valid @RequestBody CreateHrConnectionRequest req) {
        int count = bambooHrClient.testConnection(req.subdomain(), req.apiKey());
        if (count >= 0) {
            return ResponseEntity.ok(new HrTestConnectionResponse(true,
                    "Connected successfully. Found " + count + " employees.", count));
        }
        return ResponseEntity.ok(new HrTestConnectionResponse(false,
                "Failed to connect to BambooHR. Check subdomain and API key.", null));
    }

    @PostMapping("/sync")
    @RequiresFeature(FeatureKey.HR_MANAGE)
    public ResponseEntity<HrSyncRunDto> triggerSync(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        HrConnection conn = connectionRepo.findByDirectoryId(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No HR connection configured for this directory"));
        HrSyncRun run = syncService.sync(conn, HrSyncTrigger.MANUAL, principal);
        return ResponseEntity.ok(HrSyncRunDto.from(run));
    }

    @GetMapping("/sync-history")
    @RequiresFeature(FeatureKey.HR_VIEW)
    public ResponseEntity<Page<HrSyncRunDto>> getSyncHistory(
            @DirectoryId @PathVariable UUID directoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        HrConnection conn = connectionRepo.findByDirectoryId(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No HR connection configured for this directory"));
        Page<HrSyncRunDto> runs = syncRunRepo.findByHrConnectionIdOrderByStartedAtDesc(
                conn.getId(), PageRequest.of(page, size)).map(HrSyncRunDto::from);
        return ResponseEntity.ok(runs);
    }

    @GetMapping("/employees")
    @RequiresFeature(FeatureKey.HR_VIEW)
    public ResponseEntity<Page<HrEmployeeDto>> listEmployees(
            @DirectoryId @PathVariable UUID directoryId,
            @RequestParam(required = false) HrEmployeeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        HrConnection conn = connectionRepo.findByDirectoryId(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No HR connection configured for this directory"));

        PageRequest pageable = PageRequest.of(page, size, Sort.by("lastName", "firstName"));
        Page<HrEmployeeDto> employees;
        if (status != null) {
            employees = employeeRepo.findByHrConnectionIdAndStatus(conn.getId(), status, pageable)
                    .map(HrEmployeeDto::from);
        } else {
            employees = employeeRepo.findByHrConnectionId(conn.getId(), pageable)
                    .map(HrEmployeeDto::from);
        }
        return ResponseEntity.ok(employees);
    }

    @GetMapping("/employees/orphaned")
    @RequiresFeature(FeatureKey.HR_VIEW)
    public ResponseEntity<?> listOrphanedAccounts(
            @DirectoryId @PathVariable UUID directoryId) {
        HrConnection conn = connectionRepo.findByDirectoryId(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No HR connection configured for this directory"));
        var orphaned = employeeRepo.findByHrConnectionIdAndStatusAndMatchedLdapDnIsNotNull(
                conn.getId(), HrEmployeeStatus.TERMINATED);
        return ResponseEntity.ok(orphaned.stream().map(HrEmployeeDto::from).toList());
    }

    @GetMapping("/summary")
    @RequiresFeature(FeatureKey.HR_VIEW)
    public ResponseEntity<HrSyncSummaryDto> getSummary(
            @DirectoryId @PathVariable UUID directoryId) {
        HrConnection conn = connectionRepo.findByDirectoryId(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No HR connection configured for this directory"));
        UUID connId = conn.getId();

        long total = employeeRepo.countByHrConnectionId(connId);
        long active = employeeRepo.countByHrConnectionIdAndStatus(connId, HrEmployeeStatus.ACTIVE);
        long terminated = employeeRepo.countByHrConnectionIdAndStatus(connId, HrEmployeeStatus.TERMINATED);
        long matched = employeeRepo.countByHrConnectionIdAndMatchedLdapDnIsNotNull(connId);
        long unmatched = employeeRepo.countByHrConnectionIdAndMatchedLdapDnIsNull(connId);
        long orphaned = employeeRepo.countByHrConnectionIdAndStatusAndMatchedLdapDnIsNotNull(
                connId, HrEmployeeStatus.TERMINATED);

        return ResponseEntity.ok(new HrSyncSummaryDto(
                total, active, terminated, matched, unmatched, orphaned));
    }

    private void validateCron(String cron) {
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cron expression: " + cron);
        }
    }
}
