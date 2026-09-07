package com.agentcrawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AppProperties(
        String handler,
        int textChunkMaxChars,
        Crawler crawler,
        Llm llm
) {
    public record Crawler(
            int maxSearchResults,
            int maxEpisodesPerRoad,
            int requestTimeoutSeconds
    ) {}

    public record Llm(
            String apiKey,
            String baseUrl,
            String model,
            int memoryMaxMessages
    ) {}
}
