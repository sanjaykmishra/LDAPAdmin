package com.ldapadmin.entity.hr;

import com.ldapadmin.entity.enums.HrEmployeeStatus;
import com.ldapadmin.entity.enums.HrMatchConfidence;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "hr_employees",
       uniqueConstraints = @UniqueConstraint(
               name = "hr_employees_hr_connection_id_employee_id_key",
               columnNames = {"hr_connection_id", "employee_id"}))
@Getter
@Setter
@NoArgsConstructor
public class HrEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hr_connection_id", nullable = false)
    private HrConnection hrConnection;

    @Column(name = "employee_id", nullable = false, length = 100)
    private String employeeId;

    @Column(name = "work_email", length = 500)
    private String workEmail;

    @Column(name = "first_name", length = 200)
    private String firstName;

    @Column(name = "last_name", length = 200)
    private String lastName;

    @Column(name = "display_name", length = 500)
    private String displayName;

    @Column(length = 200)
    private String department;

    @Column(name = "job_title", length = 200)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private HrEmployeeStatus status;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "supervisor_id", length = 100)
    private String supervisorId;

    @Column(name = "supervisor_email", length = 500)
    private String supervisorEmail;

    @Column(name = "matched_ldap_dn", length = 1000)
    private String matchedLdapDn;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_confidence", length = 20)
    private HrMatchConfidence matchConfidence;

    @Column(name = "last_synced_at", nullable = false)
    private OffsetDateTime lastSyncedAt = OffsetDateTime.now();
}
