package com.agentcrawler.agent;

import com.agentcrawler.streaming.StreamEmitter;

public interface AgentHandler {
    void streamReply(String conversationId, String userMessage, String messageId, StreamEmitter emitter);
}
