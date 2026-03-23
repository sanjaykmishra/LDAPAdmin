package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.ldap.LdapBrowseService.BrowseResult;
import com.ldapadmin.service.LdapOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Read-only browse endpoint for directory-level users.
 *
 * <pre>
 *   GET /api/v1/directories/{directoryId}/browse?dn=...
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}/browse")
@RequiredArgsConstructor
public class DirectoryBrowseController {

    private final LdapOperationService service;

    @GetMapping
    @RequiresFeature(FeatureKey.DIRECTORY_BROWSE)
    public BrowseResult browse(@DirectoryId @PathVariable UUID directoryId,
                               @RequestParam(required = false) String dn,
                               @AuthenticationPrincipal AuthPrincipal principal) {
        return service.browse(directoryId, principal, dn);
    }
}
