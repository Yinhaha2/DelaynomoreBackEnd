package com.agentcrawler.config;

import com.agentcrawler.agent.langchain.AnimeAgent;
import com.agentcrawler.agent.langchain.AnimeVisionTool;
import com.agentcrawler.agent.langchain.LinkInspectorTool;
import com.agentcrawler.agent.langchain.ResourceCrawlTools;
import com.agentcrawler.agent.session.SessionContextTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {
    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    @Conditional(LlmApiKeyCondition.class)
    StreamingChatLanguageModel streamingChatLanguageModel(AppProperties properties) {
        AppProperties.Llm llm = properties.llm();
        String apiKey = firstNonBlank(llm.apiKey(), System.getProperty("DEEPSEEK_API_KEY"));
        log.info("正在创建 StreamingChatLanguageModel，model={}，api-key={}", llm.model(), mask(apiKey));
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(llm.baseUrl())
                .modelName(llm.model())
                .timeout(Duration.ofSeconds(120))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    @Conditional(LlmApiKeyCondition.class)
    AnimeAgent animeAgent(
            StreamingChatLanguageModel streamingChatLanguageModel,
            ResourceCrawlTools resourceCrawlTools,
            SessionContextTools sessionContextTools,
            LinkInspectorTool linkInspectorTool,
            ObjectProvider<AnimeVisionTool> animeVisionToolProvider,
            ChatMemoryProvider chatMemoryProvider
    ) {
        var builder = AiServices.builder(AnimeAgent.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider);

        AnimeVisionTool visionTool = animeVisionToolProvider.getIfAvailable();
        if (visionTool != null) {
            builder.tools(resourceCrawlTools, sessionContextTools, linkInspectorTool, visionTool);
        } else {
            builder.tools(resourceCrawlTools, sessionContextTools, linkInspectorTool);
        }
        log.info("LLM Agent 已启用（多轮对话 / 工具调用）");
        return builder.build();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String mask(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
