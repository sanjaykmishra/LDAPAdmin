package com.ldapadmin.controller.superadmin;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapBrowseService;
import com.ldapadmin.ldap.LdapBrowseService.BrowseResult;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Superadmin-only DIT browser — lists children of a DN and returns
 * the entry's attributes.
 *
 * <pre>
 *   GET /api/v1/superadmin/directories/{directoryId}/browse?dn=...
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/superadmin/directories/{directoryId}/browse")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class BrowseController {

    private final LdapBrowseService browseService;
    private final DirectoryConnectionRepository dirRepo;

    @GetMapping
    public BrowseResult browse(@PathVariable UUID directoryId,
                               @RequestParam(required = false) String dn) {
        DirectoryConnection dc = dirRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
        return browseService.browse(dc, dn);
    }
}
