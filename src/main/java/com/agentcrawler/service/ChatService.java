package com.agentcrawler.service;

import com.agentcrawler.agent.AgentHandler;
import com.agentcrawler.config.AppProperties;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.agentcrawler.core.IdGenerator;
import com.agentcrawler.model.ChatStreamRequest;
import com.agentcrawler.streaming.StreamEmitter;

import java.util.function.Consumer;

import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final ConversationService conversationService;
    private final AgentHandler agentHandler;
    private final AppProperties properties;

    public ChatService(
            ConversationService conversationService,
            AgentHandler agentHandler,
            AppProperties properties
    ) {
        this.conversationService = conversationService;
        this.agentHandler = agentHandler;
        this.properties = properties;
    }

    public void streamChat(ChatStreamRequest request, Consumer<String> frameConsumer) {
        String message = request.message().trim();
        if (message.isEmpty()) {
            frameConsumer.accept(com.agentcrawler.streaming.SseEncoder.error(
                    ErrorCode.INVALID_REQUEST.name(),
                    "message 不能为空"
            ));
            return;
        }

        String conversationId = request.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = IdGenerator.conversationId();
            conversationService.createConversation(conversationId);
        } else {
            try {
                conversationService.ensureExists(conversationId);
            } catch (AppException ex) {
                frameConsumer.accept(com.agentcrawler.streaming.SseEncoder.error(
                        ex.getCode().name(),
                        ex.getMessage()
                ));
                return;
            }
        }

        String messageId = IdGenerator.messageId();
        StreamEmitter streamEmitter = new StreamEmitter(properties.textChunkMaxChars(), frameConsumer);

        try {
            agentHandler.streamReply(conversationId, message, messageId, streamEmitter);
        } catch (AppException ex) {
            frameConsumer.accept(com.agentcrawler.streaming.SseEncoder.error(ex.getCode().name(), ex.getMessage()));
        } catch (Exception ex) {
            frameConsumer.accept(com.agentcrawler.streaming.SseEncoder.error(
                    ErrorCode.AGENT_ERROR.name(),
                    "Agent 内部错误，请稍后重试"
            ));
        }
    }
}
