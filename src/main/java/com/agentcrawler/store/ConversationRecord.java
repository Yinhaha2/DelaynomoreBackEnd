package com.agentcrawler.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConversationRecord(String id, Instant createdAt, Instant updatedAt, String title) {
    public ConversationRecord(String id, Instant createdAt) {
        this(id, createdAt, createdAt, null);
    }

    public ConversationRecord withTitle(String title) {
        Instant now = Instant.now();
        return new ConversationRecord(id, createdAt == null ? now : createdAt, now, title);
    }

    public ConversationRecord touched() {
        Instant now = Instant.now();
        return new ConversationRecord(id, createdAt == null ? now : createdAt, now, title);
    }
}
