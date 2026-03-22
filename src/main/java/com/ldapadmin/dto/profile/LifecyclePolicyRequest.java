package com.ldapadmin.dto.profile;

import com.ldapadmin.entity.enums.ExpiryAction;

public record LifecyclePolicyRequest(
        Integer expiresAfterDays,
        Integer maxRenewals,
        Integer renewalDays,
        ExpiryAction onExpiryAction,
        String onExpiryMoveDn,
        boolean onExpiryRemoveGroups,
        boolean onExpiryNotify,
        Integer warningDaysBefore) {
}
