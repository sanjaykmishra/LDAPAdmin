package com.ldapadmin.exception;

/**
 * Thrown when a caller exceeds the allowed request rate (HTTP 429).
 */
public class TooManyRequestsException extends LdapAdminException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
