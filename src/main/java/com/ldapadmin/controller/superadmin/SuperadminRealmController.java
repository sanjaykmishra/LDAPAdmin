package com.ldapadmin.controller.superadmin;

import com.ldapadmin.dto.realm.RealmResponse;
import com.ldapadmin.service.RealmService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Superadmin-only endpoint to list all realms across all directories.
 */
@RestController
@RequestMapping("/api/v1/realms")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class SuperadminRealmController {

    private final RealmService service;

    @GetMapping
    public List<RealmResponse> listAll() {
        return service.listAll();
    }
}
