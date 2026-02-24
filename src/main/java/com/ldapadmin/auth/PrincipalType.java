package com.ldapadmin.auth;

/**
 * Distinguishes the two account types that can authenticate against the API.
 */
public enum PrincipalType {
    /** Platform-level superadmin — full access, not scoped to any tenant. */
    SUPERADMIN,
    /** Tenant-scoped admin — subject to the four-dimensional permission model. */
    ADMIN
}
