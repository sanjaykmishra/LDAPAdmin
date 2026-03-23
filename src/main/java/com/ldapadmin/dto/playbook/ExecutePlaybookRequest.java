package com.ldapadmin.dto.playbook;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ExecutePlaybookRequest(
        @NotEmpty List<String> targetDns) {
}
