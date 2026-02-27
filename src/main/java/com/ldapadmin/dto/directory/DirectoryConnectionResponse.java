package com.ldapadmin.dto.directory;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.DirectoryGroupBaseDn;
import com.ldapadmin.entity.DirectoryUserBaseDn;
import com.ldapadmin.entity.enums.EnableDisableValueType;
import com.ldapadmin.entity.enums.SslMode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Directory connection response — bind password is never included. */
public record DirectoryConnectionResponse(
        UUID id,
        String displayName,
        String host,
        int port,
        SslMode sslMode,
        boolean trustAllCerts,
        String bindDn,
        String baseDn,
        String objectClasses,
        int pagingSize,
        int poolMinSize,
        int poolMaxSize,
        int poolConnectTimeoutSeconds,
        int poolResponseTimeoutSeconds,
        String enableDisableAttribute,
        EnableDisableValueType enableDisableValueType,
        String enableValue,
        String disableValue,
        UUID auditDataSourceId,
        boolean enabled,
        List<BaseDnItem> userBaseDns,
        List<BaseDnItem> groupBaseDns,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record BaseDnItem(UUID id, String dn, int displayOrder) {
        public static BaseDnItem fromUser(DirectoryUserBaseDn b) {
            return new BaseDnItem(b.getId(), b.getDn(), b.getDisplayOrder());
        }

        public static BaseDnItem fromGroup(DirectoryGroupBaseDn b) {
            return new BaseDnItem(b.getId(), b.getDn(), b.getDisplayOrder());
        }
    }

    public static DirectoryConnectionResponse from(DirectoryConnection dc,
                                                   List<DirectoryUserBaseDn> userDns,
                                                   List<DirectoryGroupBaseDn> groupDns) {
        return new DirectoryConnectionResponse(
                dc.getId(),
                dc.getDisplayName(),
                dc.getHost(),
                dc.getPort(),
                dc.getSslMode(),
                dc.isTrustAllCerts(),
                dc.getBindDn(),
                dc.getBaseDn(),
                dc.getObjectClasses(),
                dc.getPagingSize(),
                dc.getPoolMinSize(),
                dc.getPoolMaxSize(),
                dc.getPoolConnectTimeoutSeconds(),
                dc.getPoolResponseTimeoutSeconds(),
                dc.getEnableDisableAttribute(),
                dc.getEnableDisableValueType(),
                dc.getEnableValue(),
                dc.getDisableValue(),
                dc.getAuditDataSource() != null ? dc.getAuditDataSource().getId() : null,
                dc.isEnabled(),
                userDns.stream().map(BaseDnItem::fromUser).toList(),
                groupDns.stream().map(BaseDnItem::fromGroup).toList(),
                dc.getCreatedAt(),
                dc.getUpdatedAt());
    }
}
