package com.ldapadmin.service;

import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.ApprovalStatus;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.ldap.LdapConnectionFactory;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.PendingApprovalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardService {

    private final DirectoryConnectionRepository dirRepo;
    private final PendingApprovalRepository approvalRepo;
    private final AccessReviewCampaignRepository campaignRepo;
    private final AuditQueryService auditQueryService;
    private final LdapUserService userService;
    private final LdapGroupService groupService;
    private final LdapConnectionFactory connectionFactory;

    private static final String GROUP_OBJECTCLASS_FILTER =
            "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group)(objectClass=groupOfURLs))";

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard() {
        List<DirectoryConnection> dirs = dirRepo.findAll();

        List<Map<String, Object>> dirStats = new ArrayList<>();
        long totalUsers = 0;
        long totalGroups = 0;
        long totalPending = 0;

        for (DirectoryConnection dc : dirs) {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("id", dc.getId());
            stat.put("name", dc.getDisplayName());
            stat.put("enabled", dc.isEnabled());

            long userCount = 0;
            long groupCount = 0;

            if (dc.isEnabled()) {
                try {
                    // Fetch only DN (1.1 = no attributes) to minimize data transfer
                    userCount = userService.searchUsers(dc, "(objectClass=*)", null, Integer.MAX_VALUE, "1.1").size();
                } catch (Exception e) {
                    log.warn("Failed to count users for directory {}: {}", dc.getDisplayName(), e.getMessage());
                    userCount = -1;
                }

                try {
                    groupCount = groupService.searchGroups(dc, GROUP_OBJECTCLASS_FILTER, null, Integer.MAX_VALUE, "1.1").size();
                } catch (Exception e) {
                    log.warn("Failed to count groups for directory {}: {}", dc.getDisplayName(), e.getMessage());
                    groupCount = -1;
                }
            }

            stat.put("userCount", userCount);
            stat.put("groupCount", groupCount);

            long pending = approvalRepo.countByDirectoryIdAndStatus(dc.getId(), ApprovalStatus.PENDING);
            stat.put("pendingApprovals", pending);

            long activeCampaigns = campaignRepo.findByDirectoryIdAndStatus(dc.getId(), CampaignStatus.ACTIVE).size();
            stat.put("activeCampaigns", activeCampaigns);

            if (userCount >= 0) totalUsers += userCount;
            if (groupCount >= 0) totalGroups += groupCount;
            totalPending += pending;

            dirStats.add(stat);
        }

        // Recent audit events
        var recentAudit = auditQueryService.query(null, null, null, null, null, 0, 10);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("totalGroups", totalGroups);
        result.put("totalPendingApprovals", totalPending);
        result.put("directories", dirStats);
        result.put("recentAudit", recentAudit.getContent());
        return result;
    }
}
