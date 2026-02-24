package com.ldapportal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test — verifies the Spring application context loads without errors.
 * Requires a running PostgreSQL instance (provided by docker-compose for local dev).
 * Phase 2 will add unit tests using the UnboundID in-memory LDAP server.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "app.encryption.key=dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleTA=",
    "app.bootstrap.superadmin.password=test-bootstrap-pw",
    "app.jwt.secret=dGVzdHNlY3JldHRlc3RzZWNyZXR0ZXN0c2VjcmV0dGVzdHNlY3JldHRlc3Q="
})
class LdapPortalApplicationTests {

    @Test
    void contextLoads() {
        // Asserts that the Spring context assembles without errors.
    }
}
