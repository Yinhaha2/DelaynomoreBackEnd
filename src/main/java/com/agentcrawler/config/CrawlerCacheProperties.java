package com.agentcrawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Crawler-exit cache. Play URLs stay short-lived; catalog (titles / routes) may live for hours.
 * Kept off {@link AppProperties.Crawler} so that record stays a single canonical constructor.
 */
@ConfigurationProperties(prefix = "agent.crawler.cache")
public record CrawlerCacheProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("600") int playTtlSeconds,
        @DefaultValue("28800") int catalogTtlSeconds,
        @DefaultValue("2000") int playMaxEntries,
        @DefaultValue("4000") int catalogMaxEntries,
        @DefaultValue("acrawl") String keyPrefix,
        Redis redis
) {
    public CrawlerCacheProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "acrawl";
        }
        int play = playTtlSeconds <= 0 ? 600 : playTtlSeconds;
        int catalog = catalogTtlSeconds <= 0 ? 28800 : catalogTtlSeconds;
        playTtlSeconds = clamp(play, 120, 900);
        catalogTtlSeconds = clamp(catalog, 3600, 43200);
        if (playMaxEntries <= 0) {
            playMaxEntries = 2000;
        }
        if (catalogMaxEntries <= 0) {
            catalogMaxEntries = 4000;
        }
        if (redis == null) {
            redis = Redis.disabled();
        }
    }

    public static CrawlerCacheProperties disabled() {
        return new CrawlerCacheProperties(false, 600, 28800, 2000, 4000, "acrawl", Redis.disabled());
    }

    public static CrawlerCacheProperties caffeineOnly() {
        return new CrawlerCacheProperties(true, 600, 28800, 2000, 4000, "acrawl", Redis.disabled());
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    public record Redis(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("127.0.0.1") String host,
            @DefaultValue("6379") int port,
            @DefaultValue("0") int database,
            String password,
            @DefaultValue("2000") int timeoutMs
    ) {
        public Redis {
            if (host == null || host.isBlank()) {
                host = "127.0.0.1";
            }
            if (port <= 0) {
                port = 6379;
            }
            if (timeoutMs <= 0) {
                timeoutMs = 2000;
            }
            if (password == null) {
                password = "";
            }
        }

        public static Redis disabled() {
            return new Redis(false, "127.0.0.1", 6379, 0, "", 2000);
        }

        public boolean hasPassword() {
            return password != null && !password.isBlank();
        }
    }
}
