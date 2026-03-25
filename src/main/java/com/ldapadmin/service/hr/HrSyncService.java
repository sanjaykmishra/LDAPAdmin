package com.ldapadmin.service.hr;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.entity.hr.HrConnection;
import com.ldapadmin.entity.hr.HrEmployee;
import com.ldapadmin.entity.hr.HrSyncRun;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.hr.HrConnectionRepository;
import com.ldapadmin.repository.hr.HrEmployeeRepository;
import com.ldapadmin.repository.hr.HrSyncRunRepository;
import com.ldapadmin.service.AuditService;
import com.ldapadmin.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class HrSyncService {

    private final BambooHrClient bambooHrClient;
    private final HrConnectionRepository connectionRepo;
    private final HrEmployeeRepository employeeRepo;
    private final HrSyncRunRepository syncRunRepo;
    private final DirectoryConnectionRepository directoryRepo;
    private final LdapUserService ldapUserService;
    private final EncryptionService encryptionService;
    private final AuditService auditService;

    @Transactional
    public HrSyncRun sync(HrConnection connection, HrSyncTrigger trigger, AuthPrincipal principal) {
        HrSyncRun run = new HrSyncRun();
        run.setHrConnection(connection);
        run.setTriggeredBy(trigger);
        run.setStartedAt(OffsetDateTime.now());
        syncRunRepo.save(run);

        UUID directoryId = connection.getDirectory().getId();
        if (principal != null) {
            auditService.record(principal, directoryId, AuditAction.HR_SYNC_STARTED,
                    null, Map.of("provider", connection.getProvider().name(),
                            "trigger", trigger.name()));
        }

        try {
            String apiKey = encryptionService.decrypt(connection.getApiKeyEncrypted());
            List<Map<String, String>> rawEmployees = bambooHrClient.fetchAllEmployees(
                    connection.getSubdomain(), apiKey);

            DirectoryConnection directory = directoryRepo.findById(directoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Directory not found"));

            int newCount = 0;
            int updatedCount = 0;
            int matchedCount = 0;
            int unmatchedCount = 0;
            int terminatedCount = 0;
            int orphanedCount = 0;

            for (Map<String, String> raw : rawEmployees) {
                String empId = raw.getOrDefault("id", raw.get("employeeNumber"));
                if (empId == null || empId.isBlank()) continue;

                Optional<HrEmployee> existing = employeeRepo.findByHrConnectionIdAndEmployeeId(
                        connection.getId(), empId);

                HrEmployee employee;
                if (existing.isPresent()) {
                    employee = existing.get();
                    updatedCount++;
                } else {
                    employee = new HrEmployee();
                    employee.setHrConnection(connection);
                    employee.setEmployeeId(empId);
                    newCount++;
                }

                mapFields(employee, raw);
                employee.setLastSyncedAt(OffsetDateTime.now());

                // Identity matching
                String matchResult = matchEmployeeToLdap(directory, employee, connection);
                if (matchResult != null) {
                    employee.setMatchedLdapDn(matchResult);
                    employee.setMatchConfidence(HrMatchConfidence.EXACT);
                    matchedCount++;
                } else {
                    employee.setMatchedLdapDn(null);
                    employee.setMatchConfidence(HrMatchConfidence.NONE);
                    unmatchedCount++;
                }

                if (employee.getStatus() == HrEmployeeStatus.TERMINATED) {
                    terminatedCount++;
                    if (employee.getMatchedLdapDn() != null) {
                        orphanedCount++;
                    }
                }

                employeeRepo.save(employee);
            }

            // Complete the run
            run.setStatus(HrSyncStatus.SUCCESS);
            run.setCompletedAt(OffsetDateTime.now());
            run.setTotalEmployees(rawEmployees.size());
            run.setNewEmployees(newCount);
            run.setUpdatedEmployees(updatedCount);
            run.setMatchedCount(matchedCount);
            run.setUnmatchedCount(unmatchedCount);
            run.setTerminatedCount(terminatedCount);
            run.setOrphanedCount(orphanedCount);
            syncRunRepo.save(run);

            // Update connection status
            connection.setLastSyncAt(OffsetDateTime.now());
            connection.setLastSyncStatus("SUCCESS");
            connection.setLastSyncMessage(null);
            connection.setLastSyncEmployeeCount(rawEmployees.size());
            connectionRepo.save(connection);

            if (principal != null) {
                auditService.record(principal, directoryId, AuditAction.HR_SYNC_COMPLETED,
                        null, Map.of("totalEmployees", rawEmployees.size(),
                                "matched", matchedCount, "orphaned", orphanedCount));
            }

            return run;
        } catch (Exception e) {
            log.error("HR sync failed for connection {}: {}", connection.getId(), e.getMessage(), e);

            run.setStatus(HrSyncStatus.FAILED);
            run.setCompletedAt(OffsetDateTime.now());
            run.setErrorMessage(e.getMessage());
            syncRunRepo.save(run);

            connection.setLastSyncAt(OffsetDateTime.now());
            connection.setLastSyncStatus("FAILED");
            connection.setLastSyncMessage(e.getMessage());
            connectionRepo.save(connection);

            if (principal != null) {
                auditService.record(principal, directoryId, AuditAction.HR_SYNC_FAILED,
                        null, Map.of("error", e.getMessage()));
            }

            return run;
        }
    }

    String matchEmployeeToLdap(DirectoryConnection directory, HrEmployee employee,
                                       HrConnection config) {
        String searchValue = getFieldValue(employee, config.getMatchField());
        if (searchValue == null || searchValue.isBlank()) return null;

        String ldapAttr = config.getMatchAttribute();
        String filter = "(" + ldapAttr + "=" + escapeLdapFilter(searchValue) + ")";

        try {
            List<LdapUser> results = ldapUserService.searchUsers(directory, filter, null, 2, "dn");
            if (results.size() == 1) {
                return results.get(0).getDn();
            }
            if (results.size() > 1) {
                log.warn("Ambiguous LDAP match for employee {} ({}={}): {} results",
                        employee.getEmployeeId(), ldapAttr, searchValue, results.size());
            }
        } catch (Exception e) {
            log.warn("LDAP search failed for employee {}: {}", employee.getEmployeeId(), e.getMessage());
        }
        return null;
    }

    private String getFieldValue(HrEmployee employee, String field) {
        return switch (field) {
            case "workEmail" -> employee.getWorkEmail();
            case "employeeId" -> employee.getEmployeeId();
            default -> employee.getWorkEmail();
        };
    }

    private void mapFields(HrEmployee employee, Map<String, String> raw) {
        employee.setWorkEmail(raw.get("workEmail"));
        employee.setFirstName(raw.get("firstName"));
        employee.setLastName(raw.get("lastName"));
        employee.setDisplayName(raw.get("displayName"));
        employee.setDepartment(raw.get("department"));
        employee.setJobTitle(raw.get("jobTitle"));
        employee.setSupervisorId(raw.get("supervisorId"));
        employee.setSupervisorEmail(raw.get("supervisorEmail"));

        String statusStr = raw.getOrDefault("status", "Active");
        employee.setStatus(mapStatus(statusStr));

        String hireDateStr = raw.get("hireDate");
        if (hireDateStr != null && !hireDateStr.isBlank() && !"0000-00-00".equals(hireDateStr)) {
            try {
                employee.setHireDate(LocalDate.parse(hireDateStr));
            } catch (Exception ignored) { /* skip unparseable dates */ }
        }

        String termDateStr = raw.get("terminationDate");
        if (termDateStr != null && !termDateStr.isBlank() && !"0000-00-00".equals(termDateStr)) {
            try {
                employee.setTerminationDate(LocalDate.parse(termDateStr));
            } catch (Exception ignored) { /* skip unparseable dates */ }
        }
    }

    private HrEmployeeStatus mapStatus(String status) {
        if (status == null) return HrEmployeeStatus.ACTIVE;
        return switch (status.toLowerCase()) {
            case "active", "enabled" -> HrEmployeeStatus.ACTIVE;
            case "terminated", "inactive" -> HrEmployeeStatus.TERMINATED;
            case "on leave", "leave" -> HrEmployeeStatus.ON_LEAVE;
            default -> HrEmployeeStatus.ACTIVE;
        };
    }

    public static String escapeLdapFilter(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\5c");
                case '*'  -> sb.append("\\2a");
                case '('  -> sb.append("\\28");
                case ')'  -> sb.append("\\29");
                case '\0' -> sb.append("\\00");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
