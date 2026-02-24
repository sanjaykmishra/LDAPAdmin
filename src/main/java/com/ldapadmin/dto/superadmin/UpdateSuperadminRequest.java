package com.ldapadmin.dto.superadmin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateSuperadminRequest(
        @Size(max = 255) String displayName,
        @Email @Size(max = 255) String email,
        boolean active) {
}
