package com.ldapadmin.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAccountRequest(
        @NotBlank @Size(max = 255) String username,
        @Size(max = 255) String displayName,
        @Email @Size(max = 255) String email,
        boolean active) {
}
