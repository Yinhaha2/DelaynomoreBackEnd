package com.agentcrawler.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentAssemblyConfig {

    @Bean
    ChatMemoryProvider chatMemoryProvider(AppProperties properties, ChatMemoryStore chatMemoryStore) {
        int maxMessages = properties.llm().memoryMaxMessages();
        if (maxMessages < 2) {
            maxMessages = 2;
        }
        int window = maxMessages;
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(window)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }
}
