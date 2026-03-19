package com.ldapadmin.controller;

import com.ldapadmin.dto.usertemplate.UserTemplateRequest;
import com.ldapadmin.dto.usertemplate.UserTemplateResponse;
import com.ldapadmin.service.UserTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for user template definitions.
 *
 * <pre>
 *   GET    /api/v1/user-templates              — list all templates
 *   POST   /api/v1/user-templates              — create
 *   GET    /api/v1/user-templates/{id}         — get
 *   PUT    /api/v1/user-templates/{id}         — update
 *   DELETE /api/v1/user-templates/{id}         — delete
 * </pre>
 *
 * <p>User template management is restricted to superadmins.</p>
 */
@RestController
@RequestMapping("/api/v1/user-templates")
@RequiredArgsConstructor
public class UserTemplateController {

    private final UserTemplateService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public List<UserTemplateResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserTemplateResponse> create(@Valid @RequestBody UserTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public UserTemplateResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public UserTemplateResponse update(@PathVariable UUID id, @Valid @RequestBody UserTemplateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
