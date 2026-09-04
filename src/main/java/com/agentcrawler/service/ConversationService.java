package com.agentcrawler.service;

import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.agentcrawler.core.IdGenerator;
import com.agentcrawler.model.CreateConversationResponse;
import com.agentcrawler.store.ConversationRecord;
import com.agentcrawler.store.InMemoryConversationStore;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ConversationService {
    private final InMemoryConversationStore store;

    public ConversationService(InMemoryConversationStore store) {
        this.store = store;
    }

    public CreateConversationResponse create() {
        String conversationId = IdGenerator.conversationId();
        createConversation(conversationId);
        return new CreateConversationResponse(conversationId, IdGenerator.nowIso());
    }

    public void createConversation(String conversationId) {
        store.create(conversationId, Instant.now());
    }

    public void ensureExists(String conversationId) {
        ConversationRecord record = store.find(conversationId);
        if (record == null) {
            throw new AppException(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在: " + conversationId);
        }
    }
}
