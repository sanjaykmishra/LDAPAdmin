package com.ldapadmin.controller;

import com.ldapadmin.dto.userform.UserFormRequest;
import com.ldapadmin.dto.userform.UserFormResponse;
import com.ldapadmin.service.UserFormService;
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
 * CRUD for user form definitions.
 *
 * <pre>
 *   GET    /api/v1/user-forms              — list all forms
 *   POST   /api/v1/user-forms              — create
 *   GET    /api/v1/user-forms/{id}         — get
 *   PUT    /api/v1/user-forms/{id}         — update
 *   DELETE /api/v1/user-forms/{id}         — delete
 * </pre>
 *
 * <p>User form management is restricted to superadmins.</p>
 */
@RestController
@RequestMapping("/api/v1/user-forms")
@RequiredArgsConstructor
public class UserFormController {

    private final UserFormService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public List<UserFormResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserFormResponse> create(@Valid @RequestBody UserFormRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public UserFormResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public UserFormResponse update(@PathVariable UUID id, @Valid @RequestBody UserFormRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
