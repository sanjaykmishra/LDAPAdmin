package com.ldapadmin.dto.hr;

public record HrTestConnectionResponse(
        boolean success,
        String message,
        Integer employeeCount
) {}
