package com.agentcrawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AppProperties(
        String handler,
        int textChunkMaxChars,
        Crawler crawler,
        Llm llm,
        Vision vision,
        Upload upload,
        Link link
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

    public record Vision(
            String model,
            String detail,
            double minConfidenceToLock
    ) {}

    public record Upload(
            String storageDir,
            String publicBaseUrl,
            long maxSizeBytes
    ) {}

    public record Link(
            int timeoutSeconds,
            int maxLinksPerMessage,
            double minConfidenceToLock,
            boolean allowPrivateHosts
    ) {}
}
