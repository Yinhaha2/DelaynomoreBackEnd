package com.agentcrawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Per-upstream circuit breaker, probe, and Playwright bulkhead.
 * Kept off {@link AppProperties.Crawler} so that record stays a single canonical constructor.
 */
@ConfigurationProperties(prefix = "agent.crawler.reliability")
public record CrawlerReliabilityProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("60") int failureWindowSeconds,
        @DefaultValue("5") int minSamples,
        @DefaultValue("5") int openAfterFailures,
        @DefaultValue("0.5") double failureRateThreshold,
        @DefaultValue("180") int openWaitSeconds,
        @DefaultValue("30") int probeIntervalSeconds,
        @DefaultValue("2") int playwrightMaxConcurrent
) {
    public static CrawlerReliabilityProperties disabled() {
        return new CrawlerReliabilityProperties(false, 60, 5, 5, 0.5, 180, 30, 2);
    }

    public static CrawlerReliabilityProperties defaults() {
        return new CrawlerReliabilityProperties(true, 60, 5, 5, 0.5, 180, 30, 2);
    }
}
