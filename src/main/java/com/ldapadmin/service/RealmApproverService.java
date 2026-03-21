package com.ldapadmin.service;

import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.entity.RealmApprover;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.SslHelper;
import com.ldapadmin.entity.enums.SslMode;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.RealmApproverRepository;
import com.ldapadmin.repository.RealmRepository;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves approvers for a realm using dual-mode strategy:
 * <ul>
 *   <li><b>LDAP auth enabled:</b> approvers are members of a configurable LDAP group</li>
 *   <li><b>LDAP auth not enabled:</b> approvers are explicitly assigned via realm_approvers table</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealmApproverService {

    private final RealmApproverRepository approverRepo;
    private final RealmRepository realmRepo;
    private final AccountRepository accountRepo;
    private final RealmSettingService realmSettingService;
    private final ApplicationSettingsService appSettingsService;
    private final EncryptionService encryptionService;

    @Transactional(readOnly = true)
    public boolean isApprover(UUID realmId, UUID accountId) {
        if (isLdapAuthEnabled()) {
            return isLdapGroupApprover(realmId, accountId);
        }
        return approverRepo.existsByRealmIdAndAdminAccountId(realmId, accountId);
    }

    @Transactional(readOnly = true)
    public List<Account> getApprovers(UUID realmId) {
        if (isLdapAuthEnabled()) {
            return getLdapGroupApprovers(realmId);
        }
        return approverRepo.findAllByRealmId(realmId).stream()
                .map(RealmApprover::getAdminAccount)
                .toList();
    }

    @Transactional
    public void setApprovers(UUID realmId, List<UUID> accountIds) {
        if (isLdapAuthEnabled()) {
            throw new IllegalStateException(
                    "Approvers are managed via LDAP group membership when LDAP auth is enabled");
        }

        var realm = realmRepo.findById(realmId)
                .orElseThrow(() -> new ResourceNotFoundException("Realm", realmId));

        approverRepo.deleteAllByRealmId(realmId);

        for (UUID accountId : accountIds) {
            Account account = accountRepo.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
            RealmApprover ra = new RealmApprover();
            ra.setRealm(realm);
            ra.setAdminAccount(account);
            approverRepo.save(ra);
        }
    }

    public boolean isLdapAuthEnabled() {
        return appSettingsService.get().enabledAuthTypes().contains(AccountType.LDAP);
    }

    // ── LDAP group membership checks ──────────────────────────────────────────

    private boolean isLdapGroupApprover(UUID realmId, UUID accountId) {
        Optional<String> groupDn = realmSettingService.getApproverGroupDn(realmId);
        if (groupDn.isEmpty()) {
            return false;
        }

        Account account = accountRepo.findById(accountId).orElse(null);
        if (account == null || account.getLdapDn() == null) {
            return false;
        }

        return isLdapGroupMember(groupDn.get(), account.getLdapDn());
    }

    private List<Account> getLdapGroupApprovers(UUID realmId) {
        Optional<String> groupDn = realmSettingService.getApproverGroupDn(realmId);
        if (groupDn.isEmpty()) {
            return List.of();
        }

        Set<String> memberDns = readGroupMembers(groupDn.get());
        if (memberDns.isEmpty()) {
            return List.of();
        }

        // Match LDAP group members against provisioned admin accounts
        List<Account> allLdapAccounts = accountRepo.findAllByAuthType(AccountType.LDAP);
        return allLdapAccounts.stream()
                .filter(a -> a.getLdapDn() != null && memberDns.stream()
                        .anyMatch(m -> m.equalsIgnoreCase(a.getLdapDn())))
                .toList();
    }

    private boolean isLdapGroupMember(String groupDn, String memberDn) {
        Set<String> members = readGroupMembers(groupDn);
        // Check member/uniqueMember match
        if (members.stream().anyMatch(m -> m.equalsIgnoreCase(memberDn))) {
            return true;
        }
        // Check memberUid match (extract uid from memberDn)
        String uid = extractUid(memberDn);
        return uid != null && members.contains(uid);
    }

    private Set<String> readGroupMembers(String groupDn) {
        ApplicationSettings settings = appSettingsService.getEntity();
        String host = settings.getLdapAuthHost();
        Integer port = settings.getLdapAuthPort();
        SslMode sslMode = settings.getLdapAuthSslMode();

        if (host == null || host.isBlank()) {
            log.warn("LDAP auth host not configured; cannot check group membership");
            return Set.of();
        }

        try (LDAPConnection conn = openAuthConnection(settings)) {
            // Bind with service account
            String bindDn = settings.getLdapAuthBindDn();
            String bindPwEnc = settings.getLdapAuthBindPasswordEnc();
            if (bindDn != null && bindPwEnc != null) {
                conn.bind(new SimpleBindRequest(bindDn, encryptionService.decrypt(bindPwEnc)));
            }

            SearchResultEntry entry = conn.getEntry(groupDn,
                    "member", "uniqueMember", "memberUid");
            if (entry == null) {
                log.warn("Group entry not found: {}", groupDn);
                return Set.of();
            }

            Set<String> members = new LinkedHashSet<>();
            addAttributes(members, entry, "member");
            addAttributes(members, entry, "uniqueMember");
            addAttributes(members, entry, "memberUid");
            return members;

        } catch (Exception ex) {
            log.error("Failed to read LDAP group [{}]: {}", groupDn, ex.getMessage(), ex);
            return Set.of();
        }
    }

    private LDAPConnection openAuthConnection(ApplicationSettings settings) throws Exception {
        String host = settings.getLdapAuthHost();
        int port = settings.getLdapAuthPort() != null ? settings.getLdapAuthPort() : 389;
        SslMode sslMode = settings.getLdapAuthSslMode() != null
                ? settings.getLdapAuthSslMode() : SslMode.NONE;

        if (sslMode == SslMode.LDAPS) {
            SSLUtil sslUtil = SslHelper.buildSslUtil(
                    settings.isLdapAuthTrustAllCerts(), settings.getLdapAuthTrustedCertPem());
            return new LDAPConnection(sslUtil.createSSLSocketFactory(), host, port);
        }

        LDAPConnection conn = new LDAPConnection(host, port);
        if (sslMode == SslMode.STARTTLS) {
            SSLUtil sslUtil = SslHelper.buildSslUtil(
                    settings.isLdapAuthTrustAllCerts(), settings.getLdapAuthTrustedCertPem());
            conn.processExtendedOperation(
                    new StartTLSExtendedRequest(sslUtil.createSSLContext()));
        }
        return conn;
    }

    private void addAttributes(Set<String> set, SearchResultEntry entry, String attrName) {
        String[] values = entry.getAttributeValues(attrName);
        if (values != null) {
            Collections.addAll(set, values);
        }
    }

    private String extractUid(String dn) {
        try {
            String[] parts = dn.split(",");
            if (parts.length > 0 && parts[0].toLowerCase().startsWith("uid=")) {
                return parts[0].substring(4).trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
