package com.agentcrawler.store;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local conversation index. Used as L1 and as the full store when Redis is off.
 */
public class InMemoryConversationStore implements ConversationStore {
    private final Map<String, ConversationRecord> conversations = new ConcurrentHashMap<>();

    @Override
    public ConversationRecord create(String id, Instant createdAt) {
        ConversationRecord record = new ConversationRecord(id, createdAt);
        conversations.put(id, record);
        return record;
    }

    @Override
    public ConversationRecord find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return conversations.get(id);
    }

    @Override
    public void save(ConversationRecord record) {
        if (record == null || record.id() == null || record.id().isBlank()) {
            return;
        }
        conversations.put(record.id(), record);
    }

    @Override
    public void touch(String id) {
        ConversationRecord existing = find(id);
        if (existing != null) {
            conversations.put(id, existing.touched());
        }
    }
}
