package com.ldapadmin.exception;

/**
 * Thrown when an operation would violate a uniqueness constraint.
 * Maps to HTTP 409 in the REST layer.
 */
public class ConflictException extends LdapAdminException {

    public ConflictException(String message) {
        super(message);
    }
}
