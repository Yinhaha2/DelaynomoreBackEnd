package com.agentcrawler.model;

import jakarta.validation.constraints.NotBlank;

public record ChatStreamRequest(
        @NotBlank String message,
        String conversationId
) {}
