package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.ldap.CreateEntryRequest;
import com.ldapadmin.dto.ldap.LdapEntryResponse;
import com.ldapadmin.dto.ldap.MemberRequest;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.LdapOperationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * LDAP group operations for a specific directory.
 *
 * <pre>
 *   GET    /api/directories/{directoryId}/groups           — search
 *   POST   /api/directories/{directoryId}/groups           — create
 *   GET    /api/directories/{directoryId}/groups/entry     — get by DN (?dn=)
 *   DELETE /api/directories/{directoryId}/groups/entry     — delete by DN (?dn=)
 *   GET    /api/directories/{directoryId}/groups/members   — list members (?dn= &memberAttr=)
 *   POST   /api/directories/{directoryId}/groups/members   — add member (?dn=)
 *   DELETE /api/directories/{directoryId}/groups/members   — remove member (?dn=)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}/groups")
@RequiredArgsConstructor
public class GroupController {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT      = 2000;

    private final LdapOperationService service;

    // ── Search ────────────────────────────────────────────────────────────────

    @GetMapping
    public List<LdapEntryResponse> search(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String baseDn,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false, defaultValue = "") String attributes) {

        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        String[] attrArray = attributes.isBlank() ? new String[0] : attributes.split(",");
        return service.searchGroups(directoryId, principal, filter, baseDn, safeLimit, attrArray);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @RequiresFeature(FeatureKey.GROUP_CREATE_DELETE)
    public ResponseEntity<LdapEntryResponse> create(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateEntryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createGroup(directoryId, principal, req));
    }

    // ── Get / Delete by DN ────────────────────────────────────────────────────

    @GetMapping("/entry")
    public LdapEntryResponse get(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @RequestParam(required = false, defaultValue = "") String attributes) {

        String[] attrArray = attributes.isBlank() ? new String[0] : attributes.split(",");
        return service.getGroup(directoryId, principal, dn, attrArray);
    }

    @DeleteMapping("/entry")
    @RequiresFeature(FeatureKey.GROUP_CREATE_DELETE)
    public ResponseEntity<Void> delete(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn) {
        service.deleteGroup(directoryId, principal, dn);
        return ResponseEntity.noContent().build();
    }

    // ── Member management ─────────────────────────────────────────────────────

    /**
     * Lists all values of the given membership attribute for the group.
     *
     * @param dn              group distinguished name
     * @param memberAttribute attribute to read (e.g. {@code member}, {@code memberUid})
     */
    @GetMapping("/members")
    public List<String> getMembers(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @RequestParam(defaultValue = "member") String memberAttribute) {
        return service.getGroupMembers(directoryId, principal, dn, memberAttribute);
    }

    @PostMapping("/members")
    @RequiresFeature(FeatureKey.GROUP_MANAGE_MEMBERS)
    public ResponseEntity<Void> addMember(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @Valid @RequestBody MemberRequest req) {
        service.addGroupMember(directoryId, principal, dn, req.memberAttribute(), req.memberValue());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/members")
    @RequiresFeature(FeatureKey.GROUP_MANAGE_MEMBERS)
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @Valid @RequestBody MemberRequest req) {
        service.removeGroupMember(directoryId, principal, dn, req.memberAttribute(), req.memberValue());
        return ResponseEntity.noContent().build();
    }
}
