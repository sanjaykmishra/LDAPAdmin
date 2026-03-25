package com.ldapadmin.service;

import com.ldapadmin.entity.AccessSnapshot;
import com.ldapadmin.entity.AccessSnapshotMembership;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.SnapshotStatus;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.model.LdapGroup;
import com.ldapadmin.repository.AccessSnapshotMembershipRepository;
import com.ldapadmin.repository.AccessSnapshotRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccessSnapshotService {

    private final AccessSnapshotRepository snapshotRepo;
    private final AccessSnapshotMembershipRepository membershipRepo;
    private final DirectoryConnectionRepository directoryRepo;
    private final LdapGroupService ldapGroupService;

    private static final int MAX_GROUPS = 10_000;
    private static final String GROUP_FILTER =
            "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group))";

    @Transactional
    public AccessSnapshot captureSnapshot(UUID directoryId) {
        DirectoryConnection dc = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));

        AccessSnapshot snapshot = new AccessSnapshot();
        snapshot.setDirectory(dc);
        snapshot.setCapturedAt(OffsetDateTime.now());
        snapshot.setStatus(SnapshotStatus.IN_PROGRESS);
        snapshotRepo.save(snapshot);

        try {
            List<LdapGroup> groups = ldapGroupService.searchGroups(
                    dc, GROUP_FILTER, null, MAX_GROUPS, "cn", "member", "uniqueMember", "memberUid");

            Set<String> distinctUsers = new HashSet<>();
            int membershipCount = 0;

            for (LdapGroup group : groups) {
                String groupName = group.getCn() != null ? group.getCn() : group.getDn();
                List<String> members = group.getAllMembers();

                for (String memberDn : members) {
                    AccessSnapshotMembership m = new AccessSnapshotMembership();
                    m.setSnapshot(snapshot);
                    m.setUserDn(memberDn);
                    m.setGroupDn(group.getDn());
                    m.setGroupName(groupName);
                    membershipRepo.save(m);
                    distinctUsers.add(memberDn.toLowerCase());
                    membershipCount++;
                }
            }

            snapshot.setStatus(SnapshotStatus.COMPLETED);
            snapshot.setTotalUsers(distinctUsers.size());
            snapshot.setTotalGroups(groups.size());
            snapshot.setCompletedAt(OffsetDateTime.now());
            snapshotRepo.save(snapshot);

            log.info("Access snapshot completed for directory {}: {} users, {} groups, {} memberships",
                    dc.getDisplayName(), distinctUsers.size(), groups.size(), membershipCount);

            return snapshot;
        } catch (Exception e) {
            snapshot.setStatus(SnapshotStatus.FAILED);
            snapshot.setErrorMessage(e.getMessage());
            snapshot.setCompletedAt(OffsetDateTime.now());
            snapshotRepo.save(snapshot);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<AccessSnapshot> listSnapshots(UUID directoryId) {
        return snapshotRepo.findByDirectoryIdOrderByCapturedAtDesc(directoryId);
    }
}
