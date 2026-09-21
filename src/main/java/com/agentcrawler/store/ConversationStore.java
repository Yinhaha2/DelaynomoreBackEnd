package com.agentcrawler.store;

import java.time.Instant;

public interface ConversationStore {
    ConversationRecord create(String id, Instant createdAt);

    ConversationRecord find(String id);

    void save(ConversationRecord record);

    void touch(String id);
}
