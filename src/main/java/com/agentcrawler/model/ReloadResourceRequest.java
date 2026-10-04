package com.agentcrawler.model;

import jakarta.validation.constraints.NotBlank;

public record ReloadResourceRequest(
        @NotBlank String keyword,
        String site
) {
}
