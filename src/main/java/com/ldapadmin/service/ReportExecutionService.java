package com.ldapadmin.service;

import com.ldapadmin.dto.profile.GroupChangePreview;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.ProvisioningProfile;
import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.AccessReviewDecision;
import com.ldapadmin.entity.AccessReviewGroup;
import com.ldapadmin.entity.SodViolation;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.entity.enums.ReportType;
import com.ldapadmin.entity.enums.SodViolationStatus;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapGroup;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.entity.AuditEvent;
import com.ldapadmin.repository.AuditEventRepository;
import com.ldapadmin.repository.ProvisioningProfileRepository;
import com.ldapadmin.repository.SodViolationRepository;
import com.ldapadmin.util.CsvUtils;
import com.unboundid.ldap.sdk.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Executes a report on-demand and returns the results as CSV or PDF bytes.
 *
 * <h3>Report types and their queries</h3>
 * <ul>
 *   <li><b>USERS_IN_GROUP</b>     — {@code (memberOf=<groupDn>)}; param {@code groupDn} required.</li>
 *   <li><b>USERS_IN_BRANCH</b>    — {@code (objectClass=inetOrgPerson)},
 *       base = {@code branchDn}; param {@code branchDn} required.</li>
 *   <li><b>USERS_WITH_NO_GROUP</b>— {@code (&(objectClass=inetOrgPerson)(!(memberOf=*)))}.</li>
 *   <li><b>RECENTLY_ADDED</b>     — {@code (createTimestamp>=<timestamp>)}; param {@code lookbackDays}.</li>
 *   <li><b>RECENTLY_MODIFIED</b>  — {@code (modifyTimestamp>=<timestamp>)}; param {@code lookbackDays}.</li>
 *   <li><b>RECENTLY_DELETED</b>   — audit events ({@code USER_DELETE} + changelog deletes).</li>
 *   <li><b>DISABLED_ACCOUNTS</b>  — {@code (|(pwdAccountLockedTime=*)(loginDisabled=TRUE))}.</li>
 *   <li><b>MISSING_PROFILE_GROUPS</b> — profile group gap analysis.</li>
 *   <li><b>SOD_VIOLATIONS</b>     — current SoD violations from the database.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportExecutionService {

    private static final DateTimeFormatter LDAP_TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'");

    /** Maximum LDAP entries returned per report to prevent OOM. */
    private static final int MAX_LDAP_RESULTS = 10_000;

    private final LdapUserService                userService;
    private final LdapGroupService              groupService;
    private final AuditEventRepository           auditEventRepo;
    private final ProvisioningProfileRepository  profileRepo;
    private final ProvisioningProfileService     profileService;
    private final SodViolationRepository         sodViolationRepo;
    private final com.ldapadmin.repository.AccessReviewCampaignRepository campaignRepo;
    private final com.ldapadmin.repository.AccessDriftFindingRepository   driftFindingRepo;
    private final com.ldapadmin.repository.hr.HrConnectionRepository     hrConnectionRepo;
    private final com.ldapadmin.repository.hr.HrEmployeeRepository       hrEmployeeRepo;
    private final PdfReportService               pdfReportService;

    /**
     * Runs the report and returns the result as CSV or PDF bytes.
     */
    public byte[] run(DirectoryConnection dc,
                      ReportType reportType,
                      Map<String, Object> params,
                      OutputFormat format,
                      UUID directoryId) throws IOException {

        Map<String, Object> safeParams = params != null ? params : Map.of();

        ReportData data = buildReportData(dc, reportType, safeParams, directoryId);

        if (format == OutputFormat.PDF) {
            return pdfReportService.buildPdf(reportType.name().replace('_', ' '), "", data.columns, data.toRowLists());
        }
        return CsvUtils.write(data.columns, data.rows);
    }

    /**
     * Runs the report and returns the structured data (columns + rows) for inline display.
     */
    public ReportData runAsData(DirectoryConnection dc,
                                ReportType reportType,
                                Map<String, Object> params,
                                UUID directoryId) {
        Map<String, Object> safeParams = params != null ? params : Map.of();
        return buildReportData(dc, reportType, safeParams, directoryId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ReportData buildReportData(DirectoryConnection dc, ReportType reportType,
                                        Map<String, Object> params, UUID directoryId) {
        return switch (reportType) {
            case USERS_IN_GROUP         -> runUsersInGroupReportData(dc, params);
            case USERS_IN_BRANCH        -> runLdapReportData(dc,
                    "(|(objectClass=inetOrgPerson)(objectClass=person))", requireString(params, "branchDn"));
            case USERS_WITH_NO_GROUP    -> runUsersWithNoGroupReportData(dc);
            case RECENTLY_ADDED         -> runLdapReportData(dc,
                    buildRecentFilter("createTimestamp", params), null);
            case RECENTLY_MODIFIED      -> runLdapReportData(dc,
                    buildRecentFilter("modifyTimestamp", params), null);
            case RECENTLY_DELETED       -> runDeletedReportData(directoryId, params);
            case DISABLED_ACCOUNTS      -> runDisabledAccountsReportData(dc);
            case MISSING_PROFILE_GROUPS -> runMissingProfileGroupsReportData(dc, directoryId);
            case SOD_VIOLATIONS         -> runSodViolationsReportData(directoryId, params);
            case USER_ACCESS_REPORT     -> runUserAccessReportData(dc, params);
            case ACCESS_REVIEW_RESULTS  -> runAccessReviewResultsData(directoryId, params);
            case PRIVILEGED_ACCOUNT_INVENTORY -> runPrivilegedAccountInventoryData(dc, params);
            case ACCESS_DRIFT_REPORT    -> runAccessDriftReportData(directoryId);
            case TERMINATION_VELOCITY   -> runTerminationVelocityData(directoryId, params);
            case AUDIT_LOG_REPORT       -> runAuditLogReportData(directoryId, params);
        };
    }

    /**
     * User Access Report: lists all users and their group memberships.
     * Optionally filtered by a specific group DN.
     */
    private ReportData runUserAccessReportData(DirectoryConnection dc, Map<String, Object> params) {
        String groupDnFilter = params.containsKey("groupDn") ? (String) params.get("groupDn") : null;

        // Load all groups and build a DN -> group name map
        String groupFilter = "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group))";
        List<LdapGroup> allGroups = groupService.searchGroups(dc, groupFilter, null, MAX_LDAP_RESULTS,
                "cn", "member", "uniqueMember", "memberUid");

        // Build user DN -> list of group names
        Map<String, List<String>> userGroups = new HashMap<>();
        for (LdapGroup g : allGroups) {
            if (groupDnFilter != null && !groupDnFilter.isBlank()
                    && !g.getDn().equalsIgnoreCase(groupDnFilter)) {
                continue;
            }
            String groupName = g.getCn() != null ? g.getCn() : g.getDn();
            for (String memberDn : g.getAllMembers()) {
                userGroups.computeIfAbsent(memberDn.toLowerCase(), k -> new ArrayList<>()).add(groupName);
            }
        }

        // Load all users
        List<LdapUser> allUsers = userService.searchUsers(dc,
                "(|(objectClass=inetOrgPerson)(objectClass=person))", null, MAX_LDAP_RESULTS, "*");

        List<String> columns = List.of("User DN", "Name", "User ID", "Email", "Groups");
        List<Map<String, String>> rows = new ArrayList<>();
        for (LdapUser u : allUsers) {
            List<String> groups = userGroups.getOrDefault(u.getDn().toLowerCase(), List.of());
            if (groupDnFilter != null && !groupDnFilter.isBlank() && groups.isEmpty()) {
                continue; // when filtering by group, skip users not in that group
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("User DN", u.getDn());
            row.put("Name", u.getCn());
            row.put("User ID", u.getUid());
            row.put("Email", u.getMail());
            row.put("Groups", String.join("; ", groups));
            rows.add(row);
        }
        return new ReportData(columns, rows);
    }

    /**
     * Users in Group: reads the group's member/uniqueMember attributes directly
     * instead of relying on memberOf overlay.
     */
    private ReportData runUsersInGroupReportData(DirectoryConnection dc, Map<String, Object> params) {
        String groupDn = requireString(params, "groupDn");
        String groupFilter = "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group))";
        List<LdapGroup> groups = groupService.searchGroups(dc, groupFilter, null, 1,
                "cn", "member", "uniqueMember", "memberUid");

        // Read the specific group directly
        List<String> memberDns = new ArrayList<>();
        try {
            LdapGroup group = groupService.getGroup(dc, groupDn, "member", "uniqueMember", "memberUid");
            memberDns.addAll(group.getAllMembers());
        } catch (Exception e) {
            log.warn("Could not read group {}: {}", groupDn, e.getMessage());
        }

        if (memberDns.isEmpty()) {
            return new ReportData(List.of("DN", "Name", "Email", "User ID"), List.of());
        }

        // Look up each member user
        List<LdapUser> users = new ArrayList<>();
        for (String memberDn : memberDns) {
            try {
                List<LdapUser> found = userService.searchUsers(dc,
                        "(objectClass=*)", memberDn, 1, "*");
                users.addAll(found);
            } catch (Exception e) {
                // Member DN might not exist or might not be a user
                log.debug("Skipping member {}: {}", memberDn, e.getMessage());
            }
        }
        return buildReportDataFromUsers(users);
    }

    /**
     * Users with no group: two-pass approach that doesn't rely on memberOf overlay.
     * 1. Collect all DNs that appear as members in any group
     * 2. Search all users, filter out those in the member set
     */
    private ReportData runUsersWithNoGroupReportData(DirectoryConnection dc) {
        // Pass 1: collect all member DNs from all groups
        String groupFilter = "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group))";
        List<LdapGroup> allGroups = groupService.searchGroups(dc, groupFilter, null, MAX_LDAP_RESULTS,
                "member", "uniqueMember", "memberUid");

        Set<String> memberedDns = new HashSet<>();
        for (LdapGroup g : allGroups) {
            for (String m : g.getAllMembers()) {
                memberedDns.add(m.toLowerCase());
            }
        }

        // Pass 2: search all users
        List<LdapUser> allUsers = userService.searchUsers(dc,
                "(|(objectClass=inetOrgPerson)(objectClass=person))", null, MAX_LDAP_RESULTS, "*");

        // Filter to users not in any group
        List<LdapUser> ungrouped = allUsers.stream()
                .filter(u -> !memberedDns.contains(u.getDn().toLowerCase()))
                .toList();

        log.info("Users with no group: {} ungrouped out of {} total users ({} groups scanned)",
                ungrouped.size(), allUsers.size(), allGroups.size());
        return buildReportDataFromUsers(ungrouped);
    }

    /**
     * Disabled accounts: broadened filter that checks multiple disable indicators
     * across OpenLDAP, 389DS, and AD conventions.
     */
    private ReportData runDisabledAccountsReportData(DirectoryConnection dc) {
        String filter = dc.getDirectoryType() == com.ldapadmin.entity.enums.DirectoryType.ACTIVE_DIRECTORY
                ? "(userAccountControl:1.2.840.113556.1.4.803:=2)"  // AD: ACCOUNTDISABLE bit
                : "(|(pwdAccountLockedTime=*)(nsAccountLock=TRUE)(loginDisabled=TRUE)"
                  + "(employeeType=Terminated)(loginShell=/sbin/nologin))";
        return runLdapReportData(dc, filter, null);
    }

    private ReportData buildReportDataFromUsers(List<LdapUser> users) {
        TreeSet<String> attrNames = new TreeSet<>();
        users.forEach(u -> attrNames.addAll(u.getAttributes().keySet()));
        List<String> rawColumns = new ArrayList<>();
        rawColumns.add("dn");
        rawColumns.addAll(attrNames);

        // Map raw columns to friendly names
        List<String> columns = rawColumns.stream().map(ReportExecutionService::friendlyLdapColumn).toList();
        List<Map<String, String>> rows = users.stream()
                .map(u -> buildFriendlyRow(u, rawColumns))
                .toList();
        return new ReportData(columns, rows);
    }

    /** Intermediate report data container (columns + rows). */
    public record ReportData(List<String> columns, List<Map<String, String>> rows) {
        /** Convert to list-of-lists for PDF rendering. */
        List<List<String>> toRowLists() {
            return rows.stream()
                    .map(row -> columns.stream().map(c -> row.getOrDefault(c, "")).toList())
                    .toList();
        }
    }

    private ReportData runLdapReportData(DirectoryConnection dc, String filter, String baseDn) {
        List<LdapUser> users = userService.searchUsers(dc, filter, baseDn, MAX_LDAP_RESULTS, "*");
        if (users.size() >= MAX_LDAP_RESULTS) {
            log.warn("Report query hit the {} result limit — results may be truncated. "
                    + "Filter: {}, baseDn: {}", MAX_LDAP_RESULTS, filter, baseDn);
        }

        // Deterministic column order: dn first, then remaining attributes sorted
        TreeSet<String> attrNames = new TreeSet<>();
        users.forEach(u -> attrNames.addAll(u.getAttributes().keySet()));

        List<String> rawColumns = new ArrayList<>();
        rawColumns.add("dn");
        rawColumns.addAll(attrNames);

        List<String> columns = rawColumns.stream().map(ReportExecutionService::friendlyLdapColumn).toList();
        List<Map<String, String>> rows = users.stream()
                .map(u -> buildFriendlyRow(u, rawColumns))
                .toList();

        log.debug("Report query [filter={}, base={}] → {} entries", filter, baseDn, rows.size());
        return new ReportData(columns, rows);
    }

    private Map<String, String> buildFriendlyRow(LdapUser user, List<String> rawColumns) {
        Map<String, String> row = new LinkedHashMap<>();
        for (String col : rawColumns) {
            String friendly = friendlyLdapColumn(col);
            if ("dn".equals(col)) {
                row.put(friendly, user.getDn());
            } else {
                row.put(friendly, String.join("|", user.getValues(col)));
            }
        }
        return row;
    }

    private static final Map<String, String> LDAP_COLUMN_NAMES = Map.ofEntries(
            Map.entry("dn", "DN"),
            Map.entry("cn", "Name"),
            Map.entry("uid", "User ID"),
            Map.entry("mail", "Email"),
            Map.entry("sn", "Last Name"),
            Map.entry("givenname", "First Name"),
            Map.entry("displayname", "Display Name"),
            Map.entry("telephonenumber", "Phone"),
            Map.entry("title", "Title"),
            Map.entry("description", "Description"),
            Map.entry("objectclass", "Object Class"),
            Map.entry("createtimestamp", "Created"),
            Map.entry("modifytimestamp", "Modified"),
            Map.entry("employeenumber", "Employee #"),
            Map.entry("employeetype", "Employee Type"),
            Map.entry("departmentnumber", "Dept #"),
            Map.entry("o", "Organization"),
            Map.entry("ou", "Org Unit"),
            Map.entry("l", "Location"),
            Map.entry("st", "State"),
            Map.entry("postalcode", "Postal Code"),
            Map.entry("street", "Street"),
            Map.entry("memberof", "Member Of"),
            Map.entry("manager", "Manager"),
            Map.entry("loginshell", "Login Shell"),
            Map.entry("homedirectory", "Home Dir"),
            Map.entry("uidnumber", "UID #"),
            Map.entry("gidnumber", "GID #"),
            Map.entry("useraccountcontrol", "UAC"),
            Map.entry("samaccountname", "SAM Account"),
            Map.entry("userprincipalname", "UPN"),
            Map.entry("pwdaccountlockedtime", "Locked Since"),
            Map.entry("nsaccountlock", "Account Lock"),
            Map.entry("userpassword", "Password"),
            Map.entry("entryuuid", "Entry UUID"),
            Map.entry("entrydn", "Entry DN"),
            Map.entry("structuralobjectclass", "Structural Class"),
            Map.entry("subschemasubentry", "Subschema"),
            Map.entry("hassubordinates", "Has Children"),
            Map.entry("numsubordinates", "# Children")
    );

    private static String friendlyLdapColumn(String raw) {
        String friendly = LDAP_COLUMN_NAMES.get(raw.toLowerCase());
        return friendly != null ? friendly : raw;
    }

    /**
     * Queries audit events for both internal USER_DELETE and LDAP_CHANGE delete events.
     */
    private ReportData runDeletedReportData(UUID directoryId, Map<String, Object> params) {
        int lookbackDays = lookbackDays(params);
        OffsetDateTime from = OffsetDateTime.now().minusDays(lookbackDays);
        Object objectType = params.get("objectType");
        boolean includeUsers = objectType == null || objectType.toString().isBlank() || "USER".equalsIgnoreCase(objectType.toString());
        boolean includeGroups = objectType == null || objectType.toString().isBlank() || "GROUP".equalsIgnoreCase(objectType.toString());

        List<AuditEvent> allDeletes = new ArrayList<>();

        // User deletes
        if (includeUsers) {
            var internalDeletes = auditEventRepo.findAll(
                    directoryId, null, AuditAction.USER_DELETE.getDbValue(),
                    null, from, null, Pageable.unpaged());
            allDeletes.addAll(internalDeletes.getContent());
        }

        // Group deletes
        if (includeGroups) {
            var groupDeletes = auditEventRepo.findAll(
                    directoryId, null, AuditAction.GROUP_DELETE.getDbValue(),
                    null, from, null, Pageable.unpaged());
            allDeletes.addAll(groupDeletes.getContent());
        }

        // Treat combined list as "internal deletes"
        var internalDeletes = new org.springframework.data.domain.PageImpl<>(allDeletes);

        // Changelog deletes (LDAP_CHANGE events where detail contains delete indicators)
        var changelogDeletes = auditEventRepo.findAll(
                directoryId, null, AuditAction.LDAP_CHANGE.getDbValue(),
                null, from, null, Pageable.unpaged());

        List<String> columns = List.of("Entry", "Deleted By", "Deleted At", "Source");
        List<Map<String, String>> rows = new ArrayList<>();

        internalDeletes.getContent().forEach(e -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("Entry",      e.getTargetDn() != null ? e.getTargetDn() : "");
            row.put("Deleted By", e.getActorUsername() != null ? e.getActorUsername() : "");
            row.put("Deleted At", e.getOccurredAt() != null ? e.getOccurredAt().toString() : "");
            row.put("Source",     "Internal");
            rows.add(row);
        });

        changelogDeletes.getContent().stream()
                .filter(e -> e.getDetail() != null && isDeleteChange(e.getDetail()))
                .forEach(e -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("Entry",      e.getTargetDn() != null ? e.getTargetDn() : "");
                    row.put("Deleted By", "Changelog");
                    row.put("Deleted At", e.getOccurredAt() != null ? e.getOccurredAt().toString() : "");
                    row.put("Source",     "Changelog");
                    rows.add(row);
                });

        return new ReportData(columns, rows);
    }

    private boolean isDeleteChange(Map<String, Object> detail) {
        Object changeType = detail.get("changeType");
        if (changeType != null && changeType.toString().equalsIgnoreCase("delete")) return true;
        Object changes = detail.get("changes");
        return changes != null && changes.toString().toLowerCase().contains("changetype: delete");
    }

    private ReportData runMissingProfileGroupsReportData(DirectoryConnection dc, UUID directoryId) {
        List<ProvisioningProfile> profiles =
                profileRepo.findAllByDirectoryIdAndEnabledTrue(directoryId);

        List<String> columns = List.of("User", "Profile", "Missing Group", "Attribute");
        List<Map<String, String>> rows = new ArrayList<>();

        for (ProvisioningProfile profile : profiles) {
            try {
                GroupChangePreview preview =
                        profileService.evaluateGroupChanges(directoryId, profile.getId());
                for (GroupChangePreview.UserGroupChange change : preview.changes()) {
                    for (GroupChangePreview.GroupChange add : change.groupsToAdd()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("User", change.userDn());
                        row.put("Profile", profile.getName());
                        row.put("Missing Group", add.groupDn());
                        row.put("Attribute", add.memberAttribute());
                        rows.add(row);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate group changes for profile {}: {}",
                        profile.getName(), e.getMessage());
            }
        }

        return new ReportData(columns, rows);
    }

    private ReportData runSodViolationsReportData(UUID directoryId, Map<String, Object> params) {
        String policyIdStr = params.containsKey("policyId") ? (String) params.get("policyId") : null;

        List<SodViolation> violations;
        if (policyIdStr != null && !policyIdStr.isBlank()) {
            violations = sodViolationRepo.findByDirectoryIdAndPolicyId(directoryId, UUID.fromString(policyIdStr));
        } else {
            violations = sodViolationRepo.findByDirectoryId(directoryId);
        }

        List<String> columns = List.of("id", "User", "Policy", "Conflicting Groups",
                "Severity", "Status", "Detected", "Exempted By", "Exemption Reason");
        List<Map<String, String>> rows = new ArrayList<>();

        for (SodViolation v : violations) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", v.getId().toString());
            row.put("User", v.getUserDisplayName() != null && !v.getUserDisplayName().isBlank()
                    ? v.getUserDisplayName() : v.getUserDn());
            row.put("Policy", v.getPolicy().getName());
            String groupA = v.getPolicy().getGroupAName() != null ? v.getPolicy().getGroupAName() : v.getPolicy().getGroupADn();
            String groupB = v.getPolicy().getGroupBName() != null ? v.getPolicy().getGroupBName() : v.getPolicy().getGroupBDn();
            row.put("Conflicting Groups", groupA + " / " + groupB);
            row.put("Severity", v.getPolicy().getSeverity() != null ? v.getPolicy().getSeverity().name() : "");
            row.put("Status", v.getStatus().name());
            row.put("Detected", v.getDetectedAt() != null ? v.getDetectedAt().toString() : "");
            row.put("Exempted By", v.getExemptedBy() != null ? v.getExemptedBy().getUsername() : "");
            row.put("Exemption Reason", v.getExemptionReason() != null ? v.getExemptionReason() : "");
            rows.add(row);
        }

        return new ReportData(columns, rows);
    }

    // ── Audit reports ─────────────────────────────────────────────────────────

    private ReportData runAccessReviewResultsData(UUID directoryId, Map<String, Object> params) {
        String campaignIdStr = params.containsKey("campaignId") ? (String) params.get("campaignId") : null;
        if (campaignIdStr == null || campaignIdStr.isBlank()) {
            // No campaign selected — return campaign listing as fallback
            var campaigns = campaignRepo.findByDirectoryId(directoryId,
                    org.springframework.data.domain.Pageable.unpaged()).getContent();
            List<String> columns = List.of("Campaign", "Status", "Starts", "Deadline", "Completed", "Created");
            List<Map<String, String>> rows = new ArrayList<>();
            for (var c : campaigns) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("Campaign", c.getName() != null ? c.getName() : "");
                row.put("Status", c.getStatus().name());
                row.put("Starts", c.getStartsAt() != null ? c.getStartsAt().toString() : "");
                row.put("Deadline", c.getDeadline() != null ? c.getDeadline().toString() : "");
                row.put("Completed", c.getCompletedAt() != null ? c.getCompletedAt().toString() : "");
                row.put("Created", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
                rows.add(row);
            }
            return new ReportData(columns, rows);
        }

        // Campaign selected — return decision-level data
        AccessReviewCampaign campaign = campaignRepo.findById(UUID.fromString(campaignIdStr))
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignIdStr));

        List<String> columns = List.of("Group", "Member", "Decision", "Reviewer", "Decided At", "Comment");
        List<Map<String, String>> rows = new ArrayList<>();

        for (AccessReviewGroup reviewGroup : campaign.getReviewGroups()) {
            String groupLabel = reviewGroup.getGroupName() != null ? reviewGroup.getGroupName() : reviewGroup.getGroupDn();
            String reviewerName = reviewGroup.getReviewer() != null
                    ? reviewGroup.getReviewer().getUsername() : "";

            for (AccessReviewDecision decision : reviewGroup.getDecisions()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("Group", groupLabel);
                row.put("Member", decision.getMemberDisplay() != null ? decision.getMemberDisplay() : decision.getMemberDn());
                if (decision.getDecision() == null) {
                    row.put("Decision", "PENDING");
                    row.put("Reviewer", reviewerName);
                    row.put("Decided At", "");
                    row.put("Comment", "");
                } else {
                    row.put("Decision", decision.getDecision().name());
                    row.put("Reviewer", decision.getDecidedBy() != null
                            ? decision.getDecidedBy().getUsername() : reviewerName);
                    row.put("Decided At", decision.getDecidedAt() != null ? decision.getDecidedAt().toString() : "");
                    row.put("Comment", decision.getComment() != null ? decision.getComment() : "");
                }
                rows.add(row);
            }
        }
        return new ReportData(columns, rows);
    }

    /** Default LDAP filter for groups considered privileged. */
    private static final String DEFAULT_PRIVILEGED_GROUP_FILTER =
            "(&(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=group))" +
            "(|(cn=*admin*)(cn=*Admin*)(cn=*root*)(cn=*superuser*)(cn=*operator*)" +
            "(cn=*prod*)(cn=*Prod*)(cn=mgr-*)(cn=*DataAccess*)(cn=*Security*)" +
            "(cn=*privileged*)(cn=*Privileged*)(cn=Domain Admins)(cn=Enterprise Admins)" +
            "(cn=Schema Admins)(cn=Account Operators)(cn=Server Operators)))";

    private ReportData runPrivilegedAccountInventoryData(DirectoryConnection dc, Map<String, Object> params) {
        String groupFilter = params.containsKey("groupFilter") && params.get("groupFilter") != null
                && !params.get("groupFilter").toString().isBlank()
                ? params.get("groupFilter").toString()
                : DEFAULT_PRIVILEGED_GROUP_FILTER;

        List<LdapGroup> allGroups = groupService.searchGroups(dc, groupFilter, null, MAX_LDAP_RESULTS,
                "cn", "member", "uniqueMember", "description");

        List<String> columns = List.of("User", "Group DN", "Group", "Description");
        List<Map<String, String>> rows = new ArrayList<>();
        for (LdapGroup g : allGroups) {
            String groupName = g.getCn() != null ? g.getCn() : g.getDn();
            String desc = g.getAttributes().containsKey("description")
                    ? String.join("; ", g.getValues("description")) : "";
            for (String memberDn : g.getAllMembers()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("User", memberDn);
                row.put("Group DN", g.getDn());
                row.put("Group", groupName);
                row.put("Description", desc);
                rows.add(row);
            }
        }
        return new ReportData(columns, rows);
    }

    private ReportData runAccessDriftReportData(UUID directoryId) {
        var findings = driftFindingRepo.findByDirectoryId(directoryId);
        List<String> columns = List.of("id", "User", "Name", "Peer Group",
                "Anomalous Group", "Peer Match", "Severity", "Status", "Detected");
        List<Map<String, String>> rows = new ArrayList<>();
        for (var f : findings) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", f.getId().toString());
            row.put("User", f.getUserDn() != null ? f.getUserDn() : "");
            row.put("Name", f.getUserDisplay() != null ? f.getUserDisplay() : "");
            row.put("Peer Group", f.getPeerGroupValue() != null ? f.getPeerGroupValue() : "");
            row.put("Anomalous Group", f.getGroupName() != null ? f.getGroupName() : f.getGroupDn());
            row.put("Peer Match", Math.round(f.getPeerMembershipPct()) + "%");
            row.put("Severity", f.getSeverity().name());
            row.put("Status", f.getStatus().name());
            row.put("Detected", f.getDetectedAt() != null ? f.getDetectedAt().toString() : "");
            rows.add(row);
        }
        return new ReportData(columns, rows);
    }

    private ReportData runAuditLogReportData(UUID directoryId, Map<String, Object> params) {
        // Parse from/to date params
        OffsetDateTime from = parseDateTime(params, "from");
        OffsetDateTime to = parseDateTime(params, "to");
        if (from == null) from = OffsetDateTime.now().minusDays(lookbackDays(params));

        // Parse action filter
        String actionStr = params.containsKey("action") ? (String) params.get("action") : null;
        String actionDbValue = null;
        if (actionStr != null && !actionStr.isBlank()) {
            try {
                actionDbValue = AuditAction.valueOf(actionStr).getDbValue();
            } catch (IllegalArgumentException ignored) { }
        }

        var page = auditEventRepo.findAll(directoryId, null, actionDbValue, null, from, to,
                org.springframework.data.domain.PageRequest.of(0, MAX_LDAP_RESULTS));
        List<String> columns = List.of("Time", "Action", "Actor", "Target", "Directory", "Detail");
        List<Map<String, String>> rows = new ArrayList<>();
        for (var e : page.getContent()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("Time", e.getOccurredAt() != null ? e.getOccurredAt().toString() : "");
            row.put("Action", e.getAction() != null ? e.getAction().name() : "");
            row.put("Actor", e.getActorUsername() != null ? e.getActorUsername() : "");
            row.put("Target", e.getTargetDn() != null ? e.getTargetDn() : "");
            row.put("Directory", e.getDirectoryName() != null ? e.getDirectoryName() : "");
            row.put("Detail", formatDetail(e.getDetail()));
            rows.add(row);
        }
        return new ReportData(columns, rows);
    }

    private String formatDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) return "";
        return detail.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private OffsetDateTime parseDateTime(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception e1) {
            try {
                return java.time.LocalDateTime.parse(s).atOffset(java.time.ZoneOffset.UTC);
            } catch (Exception e2) {
                try {
                    return java.time.LocalDate.parse(s).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

    private ReportData runTerminationVelocityData(UUID directoryId, Map<String, Object> params) {
        // Find HR connection for this directory
        var hrConnOpt = hrConnectionRepo.findByDirectoryId(directoryId);
        if (hrConnOpt.isEmpty()) {
            return new ReportData(
                    List.of("Employee", "Termination Date", "Access Revoked At", "Velocity", "SLA Status"),
                    List.of());
        }
        var hrConn = hrConnOpt.get();

        // SLA threshold in hours (default 24)
        int slaHours = 24;
        Object slaRaw = params.get("slaHours");
        if (slaRaw instanceof Number n) slaHours = n.intValue();
        else if (slaRaw instanceof String s) { try { slaHours = Integer.parseInt(s); } catch (NumberFormatException ignored) { } }

        // Date range
        OffsetDateTime from = parseDateTime(params, "from");
        if (from == null) from = OffsetDateTime.now().minusDays(lookbackDays(params));
        OffsetDateTime to = parseDateTime(params, "to");

        // Get all terminated employees
        var employees = hrEmployeeRepo.findByHrConnectionIdAndStatusAndMatchedLdapDnIsNotNull(
                hrConn.getId(), com.ldapadmin.entity.enums.HrEmployeeStatus.TERMINATED);

        // Filter by date range using terminationDate
        final OffsetDateTime fromFinal = from;
        final OffsetDateTime toFinal = to;
        var filtered = employees.stream()
                .filter(e -> e.getTerminationDate() != null)
                .filter(e -> {
                    OffsetDateTime termAt = e.getTerminationDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
                    if (termAt.isBefore(fromFinal)) return false;
                    return toFinal == null || !termAt.isAfter(toFinal);
                })
                .toList();

        List<String> columns = List.of("Employee", "Termination Date", "Access Revoked At", "Velocity", "SLA Status");
        List<Map<String, String>> rows = new ArrayList<>();

        for (var emp : filtered) {
            String empName = emp.getDisplayName() != null && !emp.getDisplayName().isBlank()
                    ? emp.getDisplayName()
                    : ((emp.getFirstName() != null ? emp.getFirstName() : "") + " " +
                       (emp.getLastName() != null ? emp.getLastName() : "")).trim();
            String ldapDn = emp.getMatchedLdapDn();
            OffsetDateTime termDate = emp.getTerminationDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC);

            // Find the earliest USER_DELETE or USER_DISABLE audit event for this user after termination
            var auditEvents = auditEventRepo.findAll(
                    directoryId, null, null, ldapDn, termDate, null,
                    org.springframework.data.domain.PageRequest.of(0, 10));

            OffsetDateTime revokedAt = null;
            for (var ae : auditEvents.getContent()) {
                if (ae.getAction() != null &&
                        (ae.getAction() == AuditAction.USER_DELETE || ae.getAction() == AuditAction.USER_DISABLE)) {
                    revokedAt = ae.getOccurredAt();
                    break;
                }
            }

            // Calculate velocity
            String velocity;
            String slaStatus;
            if (revokedAt != null) {
                long hours = java.time.Duration.between(termDate, revokedAt).toHours();
                long minutes = java.time.Duration.between(termDate, revokedAt).toMinutes() % 60;
                if (hours < 1) velocity = minutes + "m";
                else if (hours < 48) velocity = hours + "h " + minutes + "m";
                else velocity = (hours / 24) + "d " + (hours % 24) + "h";
                slaStatus = hours <= slaHours ? "Within SLA" : "Overdue";
            } else {
                long hours = java.time.Duration.between(termDate, OffsetDateTime.now()).toHours();
                if (hours < 48) velocity = hours + "h (pending)";
                else velocity = (hours / 24) + "d (pending)";
                slaStatus = hours <= slaHours ? "Pending" : "Overdue";
            }

            Map<String, String> row = new LinkedHashMap<>();
            row.put("Employee", empName);
            row.put("Termination Date", termDate.toString());
            row.put("Access Revoked At", revokedAt != null ? revokedAt.toString() : "");
            row.put("Velocity", velocity);
            row.put("SLA Status", slaStatus);
            rows.add(row);
        }

        return new ReportData(columns, rows);
    }

    private String buildGroupFilter(Map<String, Object> params) {
        String groupDn = requireString(params, "groupDn");
        return "(memberOf=" + Filter.encodeValue(groupDn) + ")";
    }

    /**
     * Builds an LDAP filter for recently-added/modified reports with optional
     * object type filtering (USER, GROUP, or all).
     */
    private String buildRecentFilter(String timestampAttr, Map<String, Object> params) {
        String ts = lookbackTimestamp(params);
        String timeFilter = "(" + timestampAttr + ">=" + ts + ")";
        Object objectType = params.get("objectType");
        if (objectType == null || objectType.toString().isBlank()) {
            return timeFilter;
        }
        String typeFilter = switch (objectType.toString().toUpperCase()) {
            case "USER" -> "(|(objectClass=inetOrgPerson)(&(objectClass=user)(!(objectClass=computer))))";
            case "GROUP" -> "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group))";
            default -> "";
        };
        if (typeFilter.isEmpty()) return timeFilter;
        return "(&" + timeFilter + typeFilter + ")";
    }

    private String lookbackTimestamp(Map<String, Object> params) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(lookbackDays(params));
        return cutoff.withOffsetSameInstant(java.time.ZoneOffset.UTC)
                     .format(LDAP_TIMESTAMP_FMT);
    }

    private int lookbackDays(Map<String, Object> params) {
        Object raw = params.get("lookbackDays");
        if (raw instanceof Number n) return n.intValue();
        if (raw instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        }
        return 30;
    }

    private String requireString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "Report parameter '" + key + "' is required for this report type");
        }
        return value.toString();
    }
}
