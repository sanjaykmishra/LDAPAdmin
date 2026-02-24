package com.ldapadmin.repository;

import com.ldapadmin.entity.SuperadminAccount;
import com.ldapadmin.entity.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SuperadminAccountRepository extends JpaRepository<SuperadminAccount, UUID> {

    Optional<SuperadminAccount> findByUsername(String username);

    Optional<SuperadminAccount> findByUsernameAndActiveTrue(String username);

    List<SuperadminAccount> findAllByAccountType(AccountType accountType);

    /** Used by the bootstrap logic: if this returns > 0 the bootstrap credential is invalid. */
    long countByAccountTypeAndActiveTrue(AccountType accountType);

    /** Prevents deletion of the last active LOCAL superadmin. */
    long countByAccountTypeAndActiveTrueAndIdNot(AccountType accountType, UUID excludeId);

    @Query("SELECT s FROM SuperadminAccount s WHERE s.ldapSourceDirectory.id = :directoryId")
    List<SuperadminAccount> findAllByLdapSourceDirectoryId(UUID directoryId);
}
