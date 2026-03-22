package com.ldapadmin.dto.profile;

import com.ldapadmin.entity.ProfileLifecyclePolicy;
import com.ldapadmin.entity.enums.ExpiryAction;

import java.util.UUID;

public record LifecyclePolicyResponse(
        UUID id,
        UUID profileId,
        Integer expiresAfterDays,
        Integer maxRenewals,
        Integer renewalDays,
        ExpiryAction onExpiryAction,
        String onExpiryMoveDn,
        boolean onExpiryRemoveGroups,
        boolean onExpiryNotify,
        Integer warningDaysBefore) {

    public static LifecyclePolicyResponse from(ProfileLifecyclePolicy p) {
        return new LifecyclePolicyResponse(
                p.getId(),
                p.getProfile().getId(),
                p.getExpiresAfterDays(),
                p.getMaxRenewals(),
                p.getRenewalDays(),
                p.getOnExpiryAction(),
                p.getOnExpiryMoveDn(),
                p.isOnExpiryRemoveGroups(),
                p.isOnExpiryNotify(),
                p.getWarningDaysBefore());
    }
}
