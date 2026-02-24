package com.ldapadmin.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed access to the {@code app.*} configuration namespace defined in
 * {@code application.yml}.  Validated at startup so missing/blank required
 * values fail fast with a meaningful error message.
 */
@ConfigurationProperties(prefix = "app")
@Validated
@Getter
@Setter
public class AppProperties {

    @Valid
    private Encryption encryption = new Encryption();

    @Valid
    private Bootstrap bootstrap = new Bootstrap();

    @Valid
    private Jwt jwt = new Jwt();

    // ── Nested config classes ─────────────────────────────────────────────────

    @Getter
    @Setter
    public static class Encryption {
        /** Base64-encoded 32-byte AES-256 key.  Loaded from ENCRYPTION_KEY env var. */
        @NotBlank
        private String key;
    }

    @Getter
    @Setter
    public static class Bootstrap {
        @Valid
        private Superadmin superadmin = new Superadmin();

        @Getter
        @Setter
        public static class Superadmin {
            @NotBlank
            private String username;
            @NotBlank
            private String password;
        }
    }

    @Getter
    @Setter
    public static class Jwt {
        /** Base64-encoded long random secret.  Loaded from JWT_SECRET env var. */
        @NotBlank
        private String secret;

        @Positive
        private int expiryMinutes = 60;
    }
}
