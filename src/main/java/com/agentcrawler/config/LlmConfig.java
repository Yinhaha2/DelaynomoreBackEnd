package com.agentcrawler.config;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {

    @Bean
    @ConditionalOnExpression("'${agent.llm.api-key:}'.length() > 0")
    StreamingChatLanguageModel streamingChatLanguageModel(AppProperties properties) {
        AppProperties.Llm llm = properties.llm();
        return OpenAiStreamingChatModel.builder()
                .apiKey(llm.apiKey())
                .baseUrl(llm.baseUrl())
                .modelName(llm.model())
                .timeout(Duration.ofSeconds(120))
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
