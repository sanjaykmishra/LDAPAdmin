package com.ldapadmin.repository.hr;

import com.ldapadmin.entity.enums.HrEmployeeStatus;
import com.ldapadmin.entity.hr.HrEmployee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HrEmployeeRepository extends JpaRepository<HrEmployee, UUID> {

    Page<HrEmployee> findByHrConnectionId(UUID hrConnectionId, Pageable pageable);

    Page<HrEmployee> findByHrConnectionIdAndStatus(UUID hrConnectionId, HrEmployeeStatus status, Pageable pageable);

    List<HrEmployee> findAllByHrConnectionId(UUID hrConnectionId);

    Optional<HrEmployee> findByHrConnectionIdAndEmployeeId(UUID hrConnectionId, String employeeId);

    List<HrEmployee> findByHrConnectionIdAndMatchedLdapDnIsNull(UUID hrConnectionId);

    List<HrEmployee> findByHrConnectionIdAndStatusAndMatchedLdapDnIsNotNull(
            UUID hrConnectionId, HrEmployeeStatus status);

    long countByHrConnectionIdAndStatus(UUID hrConnectionId, HrEmployeeStatus status);

    long countByHrConnectionIdAndMatchedLdapDnIsNotNull(UUID hrConnectionId);

    long countByHrConnectionIdAndMatchedLdapDnIsNull(UUID hrConnectionId);

    long countByHrConnectionId(UUID hrConnectionId);

    long countByHrConnectionIdAndStatusAndMatchedLdapDnIsNotNull(
            UUID hrConnectionId, HrEmployeeStatus status);

    void deleteByHrConnectionId(UUID hrConnectionId);
}
