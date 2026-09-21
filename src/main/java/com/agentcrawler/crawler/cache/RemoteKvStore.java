package com.agentcrawler.crawler.cache;

import java.time.Duration;

/**
 * Optional remote KV. Implementations must not throw to callers: Redis outages degrade to local/miss.
 */
public interface RemoteKvStore {
    String get(String key);

    void set(String key, String value, Duration ttl);

    default void delete(String key) {}

    default void expire(String key, Duration ttl) {}

    default void close() {}

    static RemoteKvStore noop() {
        return new RemoteKvStore() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public void set(String key, String value, Duration ttl) {
            }
        };
    }
}
