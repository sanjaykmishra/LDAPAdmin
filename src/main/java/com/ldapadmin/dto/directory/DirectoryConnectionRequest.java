package com.ldapadmin.dto.directory;

import com.ldapadmin.entity.enums.EnableDisableValueType;
import com.ldapadmin.entity.enums.SslMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Create / update request for a directory connection.
 *
 * <p>{@code bindPassword} is the plaintext password — the service layer
 * encrypts it before persisting.  On update, pass {@code null} to keep
 * the existing encrypted value.</p>
 */
public record DirectoryConnectionRequest(
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(max = 255) String host,
        @Min(1) @Max(65535) int port,
        @NotNull SslMode sslMode,
        boolean trustAllCerts,
        String trustedCertificatePem,
        @NotBlank String bindDn,
        String bindPassword,       // plaintext; null on update = keep existing
        @NotBlank String baseDn,
        @Min(1) @Max(5000) int pagingSize,
        @Min(1) int poolMinSize,
        @Min(1) int poolMaxSize,
        @Min(1) int poolConnectTimeoutSeconds,
        @Min(1) int poolResponseTimeoutSeconds,
        String enableDisableAttribute,
        EnableDisableValueType enableDisableValueType,
        String enableValue,
        String disableValue,
        UUID auditDataSourceId,
        boolean enabled,
        @Valid List<BaseDnRequest> userBaseDns,
        @Valid List<BaseDnRequest> groupBaseDns) {
}
