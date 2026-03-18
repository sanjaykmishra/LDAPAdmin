package com.ldapadmin.dto.ldap;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordLdapRequest(@NotBlank String newPassword) {
}
