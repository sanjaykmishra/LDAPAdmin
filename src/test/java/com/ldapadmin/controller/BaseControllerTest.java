package com.ldapadmin.controller;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.JwtTokenService;
import com.ldapadmin.auth.PermissionService;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.config.SecurityConfig;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

/**
 * Base class for {@code @WebMvcTest} controller tests.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@code @MockBean JpaMetamodelMappingContext} — prevents
 *       {@code @EnableJpaAuditing} from failing in the web slice context.</li>
 *   <li>{@code @MockBean JwtTokenService} — satisfies
 *       {@link com.ldapadmin.auth.JwtAuthenticationFilter}.</li>
 *   <li>{@code @MockBean PermissionService} — satisfies
 *       {@link com.ldapadmin.auth.FeaturePermissionAspect} if loaded.</li>
 *   <li>Shared test property source with required {@code app.*} config.</li>
 *   <li>Factory methods for superadmin and admin authentication tokens.</li>
 * </ul>
 * </p>
 */
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.jwt.secret=dGVzdHNlY3JldHRlc3RzZWNyZXR0ZXN0c2VjcmV0dGVzdHNlY3JldHRlc3Q=",
        "app.encryption.key=dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleTA=",
        "app.bootstrap.superadmin.username=admin",
        "app.bootstrap.superadmin.password=test"
})
public abstract class BaseControllerTest {

    @MockBean
    @SuppressWarnings("unused")
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    @SuppressWarnings("unused")
    JwtTokenService jwtTokenService;

    @MockBean
    @SuppressWarnings("unused")
    PermissionService permissionService;

    // ── Auth helpers ──────────────────────────────────────────────────────────

    protected static UsernamePasswordAuthenticationToken superadminAuth() {
        AuthPrincipal p = new AuthPrincipal(PrincipalType.SUPERADMIN, UUID.randomUUID(), "superadmin");
        return new UsernamePasswordAuthenticationToken(p, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
    }

    protected static UsernamePasswordAuthenticationToken adminAuth() {
        AuthPrincipal p = new AuthPrincipal(PrincipalType.ADMIN, UUID.randomUUID(), "admin");
        return new UsernamePasswordAuthenticationToken(p, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
