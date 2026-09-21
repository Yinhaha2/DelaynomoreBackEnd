package com.agentcrawler.service;

import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.agentcrawler.core.IdGenerator;
import com.agentcrawler.model.CreateConversationResponse;
import com.agentcrawler.store.ConversationRecord;
import com.agentcrawler.store.ConversationStore;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ConversationService {
    private final ConversationStore store;

    public ConversationService(ConversationStore store) {
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
        store.touch(conversationId);
    }

    public void updateTitle(String conversationId, String title) {
        if (conversationId == null || conversationId.isBlank() || title == null || title.isBlank()) {
            return;
        }
        ConversationRecord record = store.find(conversationId);
        if (record == null) {
            return;
        }
        store.save(record.withTitle(title));
    }
}
