package com.agentcrawler.model;

import java.util.List;

public record ChatStreamRequest(
        String message,
        String conversationId,
        List<ChatAttachment> attachments
) {
    public ChatStreamRequest {
        if (attachments == null) {
            attachments = List.of();
        }
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }
}
