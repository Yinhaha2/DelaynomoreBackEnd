package com.agentcrawler.agent;

import com.agentcrawler.model.ChatAttachment;
import com.agentcrawler.streaming.StreamEmitter;

import java.util.List;

public interface AgentHandler {
    void streamReply(
            String conversationId,
            String userMessage,
            List<ChatAttachment> attachments,
            boolean needTitle,
            String messageId,
            StreamEmitter emitter
    );
}
