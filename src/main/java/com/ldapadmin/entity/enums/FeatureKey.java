package com.ldapadmin.entity.enums;

import java.util.Arrays;

/**
 * The twelve feature permission keys defined in §3.2.
 * DB values use dot notation (e.g. "user.create"); a custom JPA converter
 * handles the mapping because Java enum constants cannot contain dots.
 */
public enum FeatureKey {

    USER_CREATE          ("user.create"),
    USER_EDIT            ("user.edit"),
    USER_DELETE          ("user.delete"),
    USER_ENABLE_DISABLE  ("user.enable_disable"),
    USER_MOVE            ("user.move"),
    GROUP_MANAGE_MEMBERS ("group.manage_members"),
    GROUP_CREATE_DELETE  ("group.create_delete"),
    BULK_IMPORT          ("bulk.import"),
    BULK_EXPORT          ("bulk.export"),
    REPORTS_RUN          ("reports.run"),
    REPORTS_EXPORT       ("reports.export"),
    REPORTS_SCHEDULE     ("reports.schedule");

    private final String dbValue;

    FeatureKey(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static FeatureKey fromDbValue(String value) {
        return Arrays.stream(values())
            .filter(k -> k.dbValue.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown feature key: " + value));
    }
}
