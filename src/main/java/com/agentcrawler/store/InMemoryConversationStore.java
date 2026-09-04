package com.agentcrawler.store;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryConversationStore {
    private final Map<String, ConversationRecord> conversations = new ConcurrentHashMap<>();

    public ConversationRecord create(String id, Instant createdAt) {
        ConversationRecord record = new ConversationRecord(id, createdAt);
        conversations.put(id, record);
        return record;
    }

    public ConversationRecord find(String id) {
        return conversations.get(id);
    }
}
