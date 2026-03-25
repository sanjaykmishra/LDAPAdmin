package com.ldapadmin.service;

import com.ldapadmin.entity.AccessSnapshot;
import com.ldapadmin.entity.AccessSnapshotMembership;
import com.ldapadmin.entity.AccessSnapshotUser;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.SnapshotStatus;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapGroup;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.AccessSnapshotMembershipRepository;
import com.ldapadmin.repository.AccessSnapshotRepository;
import com.ldapadmin.repository.AccessSnapshotUserRepository;
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
    private final AccessSnapshotUserRepository userRepo;
    private final DirectoryConnectionRepository directoryRepo;
    private final LdapGroupService ldapGroupService;
    private final LdapUserService ldapUserService;

    private static final int MAX_GROUPS = 10_000;
    private static final int MAX_USERS = 50_000;
    private static final int BATCH_SIZE = 500;
    private static final int RETENTION_DAYS = 90;
    private static final String GROUP_FILTER =
            "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group))";
    private static final String USER_FILTER =
            "(|(objectClass=inetOrgPerson)(&(objectClass=user)(!(objectClass=computer))))";

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
            // 1. Capture group memberships in batches
            List<LdapGroup> groups = ldapGroupService.searchGroups(
                    dc, GROUP_FILTER, null, MAX_GROUPS, "cn", "member", "uniqueMember", "memberUid");

            Set<String> distinctUsers = new HashSet<>();
            List<AccessSnapshotMembership> batch = new ArrayList<>();

            for (LdapGroup group : groups) {
                String groupName = group.getCn() != null ? group.getCn() : group.getDn();
                for (String memberDn : group.getAllMembers()) {
                    AccessSnapshotMembership m = new AccessSnapshotMembership();
                    m.setSnapshot(snapshot);
                    m.setUserDn(memberDn);
                    m.setGroupDn(group.getDn());
                    m.setGroupName(groupName);
                    batch.add(m);
                    distinctUsers.add(memberDn.toLowerCase());

                    if (batch.size() >= BATCH_SIZE) {
                        membershipRepo.saveAll(batch);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) membershipRepo.saveAll(batch);

            // 2. Capture user attributes for peer grouping (at snapshot time, not analysis time)
            List<LdapUser> users = ldapUserService.searchUsers(dc, USER_FILTER, null, MAX_USERS,
                    "cn", "displayName", "department", "title", "ou");
            List<AccessSnapshotUser> userBatch = new ArrayList<>();

            for (LdapUser user : users) {
                AccessSnapshotUser su = new AccessSnapshotUser();
                su.setSnapshot(snapshot);
                su.setUserDn(user.getDn());
                su.setDisplayName(user.getDisplayName() != null ? user.getDisplayName() : user.getCn());
                su.setDepartment(user.getFirstValue("department"));
                su.setTitle(user.getFirstValue("title"));
                su.setOu(user.getFirstValue("ou"));
                userBatch.add(su);

                if (userBatch.size() >= BATCH_SIZE) {
                    userRepo.saveAll(userBatch);
                    userBatch.clear();
                }
            }
            if (!userBatch.isEmpty()) userRepo.saveAll(userBatch);

            snapshot.setStatus(SnapshotStatus.COMPLETED);
            snapshot.setTotalUsers(distinctUsers.size());
            snapshot.setTotalGroups(groups.size());
            snapshot.setCompletedAt(OffsetDateTime.now());
            snapshotRepo.save(snapshot);

            log.info("Snapshot completed for directory '{}': {} users, {} groups",
                    dc.getDisplayName(), distinctUsers.size(), groups.size());

            // 3. Purge old snapshots
            purgeOldSnapshots(directoryId);

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

    private void purgeOldSnapshots(UUID directoryId) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RETENTION_DAYS);
        List<AccessSnapshot> old = snapshotRepo.findByDirectoryIdOrderByCapturedAtDesc(directoryId).stream()
                .filter(s -> s.getCapturedAt().isBefore(cutoff))
                .toList();
        if (!old.isEmpty()) {
            snapshotRepo.deleteAll(old);
            log.info("Purged {} old snapshots older than {} days", old.size(), RETENTION_DAYS);
        }
    }
}
