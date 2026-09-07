package com.agentcrawler.config;

import com.agentcrawler.agent.langchain.AnimeAgent;
import com.agentcrawler.agent.langchain.ResourceCrawlTools;
import com.agentcrawler.agent.session.SessionContextTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentAssemblyConfig {

    @Bean
    ChatMemoryProvider chatMemoryProvider(AppProperties properties) {
        int maxMessages = properties.llm().memoryMaxMessages();
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    @ConditionalOnBean(StreamingChatLanguageModel.class)
    AnimeAgent animeAgent(
            StreamingChatLanguageModel streamingChatLanguageModel,
            ResourceCrawlTools resourceCrawlTools,
            SessionContextTools sessionContextTools,
            ChatMemoryProvider chatMemoryProvider
    ) {
        return AiServices.builder(AnimeAgent.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(resourceCrawlTools, sessionContextTools)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }
}
