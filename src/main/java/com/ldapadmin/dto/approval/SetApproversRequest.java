package com.ldapadmin.dto.approval;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SetApproversRequest(@NotNull List<UUID> accountIds) {
}
