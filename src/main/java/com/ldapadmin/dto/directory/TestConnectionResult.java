package com.ldapadmin.dto.directory;

public record TestConnectionResult(
        boolean success,
        String message,
        long elapsedMs) {
}
