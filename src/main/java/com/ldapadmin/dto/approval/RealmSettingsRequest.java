package com.ldapadmin.dto.approval;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record RealmSettingsRequest(@NotNull Map<String, String> settings) {
}
