package com.agentcrawler.crawler.cache;

import java.time.Duration;

/**
 * Optional remote KV. Implementations must not throw to callers: Redis outages degrade to local/miss.
 */
public interface RemoteKvStore {
    String get(String key);

    void set(String key, String value, Duration ttl);

    default void close() {}
}
