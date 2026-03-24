package com.ldapadmin.exception;

/**
 * Thrown when a group membership assignment would violate a Separation of Duties policy
 * configured with action=BLOCK. Maps to HTTP 409 in the REST layer.
 */
public class SodViolationException extends ConflictException {

    public SodViolationException(String message) {
        super(message);
    }
}
