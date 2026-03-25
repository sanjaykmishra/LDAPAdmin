package com.ldapadmin.dto.hr;

import com.ldapadmin.entity.enums.HrEmployeeStatus;
import com.ldapadmin.entity.enums.HrMatchConfidence;
import com.ldapadmin.entity.hr.HrEmployee;

import java.time.LocalDate;
import java.util.UUID;

public record HrEmployeeDto(
        UUID id,
        String employeeId,
        String workEmail,
        String firstName,
        String lastName,
        String displayName,
        String department,
        String jobTitle,
        HrEmployeeStatus status,
        LocalDate hireDate,
        LocalDate terminationDate,
        String supervisorId,
        String supervisorEmail,
        String matchedLdapDn,
        HrMatchConfidence matchConfidence
) {
    public static HrEmployeeDto from(HrEmployee e) {
        return new HrEmployeeDto(
                e.getId(),
                e.getEmployeeId(),
                e.getWorkEmail(),
                e.getFirstName(),
                e.getLastName(),
                e.getDisplayName(),
                e.getDepartment(),
                e.getJobTitle(),
                e.getStatus(),
                e.getHireDate(),
                e.getTerminationDate(),
                e.getSupervisorId(),
                e.getSupervisorEmail(),
                e.getMatchedLdapDn(),
                e.getMatchConfidence()
        );
    }
}
