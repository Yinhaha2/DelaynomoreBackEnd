package com.agentcrawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Conversation meta / blackboard / ChatMemory. Separate prefix and TTL from crawler play URLs.
 */
@ConfigurationProperties(prefix = "agent.session")
public record SessionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("604800") int ttlSeconds,
        @DefaultValue("2000") int maxEntries,
        @DefaultValue("asess") String keyPrefix
) {
    public SessionProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "asess";
        }
        int ttl = ttlSeconds <= 0 ? 604_800 : ttlSeconds;
        ttlSeconds = clamp(ttl, 3_600, 2_592_000);
        if (maxEntries <= 0) {
            maxEntries = 2000;
        }
    }

    public static SessionProperties localOnly() {
        return new SessionProperties(true, 604_800, 2000, "asess");
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
