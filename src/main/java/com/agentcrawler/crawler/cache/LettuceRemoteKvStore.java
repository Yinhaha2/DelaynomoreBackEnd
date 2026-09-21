package com.agentcrawler.crawler.cache;

import com.agentcrawler.config.CrawlerCacheProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Lazy Redis connection. Any failure is logged and treated as a miss / no-op.
 */
public class LettuceRemoteKvStore implements RemoteKvStore {
    private static final Logger log = LoggerFactory.getLogger(LettuceRemoteKvStore.class);

    private static final long FAILURE_COOLDOWN_MS = 5_000;

    private final CrawlerCacheProperties.Redis settings;
    private final Object lock = new Object();
    private volatile RedisClient client;
    private volatile StatefulRedisConnection<String, String> connection;
    private volatile long skipUntilMs;

    public LettuceRemoteKvStore(CrawlerCacheProperties.Redis settings) {
        this.settings = settings;
    }

    @Override
    public String get(String key) {
        if (coolingDown()) {
            return null;
        }
        try {
            RedisCommands<String, String> commands = commands();
            if (commands == null) {
                return null;
            }
            return commands.get(key);
        } catch (RuntimeException ex) {
            log.warn("Redis GET 失败，降级本地/直连爬虫: {}", ex.toString());
            markFailure();
            return null;
        }
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        if (coolingDown()) {
            return;
        }
        try {
            RedisCommands<String, String> commands = commands();
            if (commands == null || value == null) {
                return;
            }
            long seconds = Math.max(1, ttl.toSeconds());
            commands.setex(key, seconds, value);
        } catch (RuntimeException ex) {
            log.warn("Redis SET 失败，已写入本地缓存: {}", ex.toString());
            markFailure();
        }
    }

    @Override
    public void delete(String key) {
        if (coolingDown()) {
            return;
        }
        try {
            RedisCommands<String, String> commands = commands();
            if (commands == null || key == null) {
                return;
            }
            commands.del(key);
        } catch (RuntimeException ex) {
            log.warn("Redis DEL 失败: {}", ex.toString());
            markFailure();
        }
    }

    @Override
    public void expire(String key, Duration ttl) {
        if (coolingDown() || key == null) {
            return;
        }
        try {
            RedisCommands<String, String> commands = commands();
            if (commands == null) {
                return;
            }
            long seconds = Math.max(1, ttl.toSeconds());
            commands.expire(key, seconds);
        } catch (RuntimeException ex) {
            log.warn("Redis EXPIRE 失败: {}", ex.toString());
            markFailure();
        }
    }

    @PreDestroy
    @Override
    public void close() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (RuntimeException ignored) {
                    // already closed
                }
                connection = null;
            }
            if (client != null) {
                try {
                    client.shutdown();
                } catch (RuntimeException ignored) {
                    // already closed
                }
                client = null;
            }
        }
    }

    private RedisCommands<String, String> commands() {
        StatefulRedisConnection<String, String> conn = connect();
        return conn == null ? null : conn.sync();
    }

    private StatefulRedisConnection<String, String> connect() {
        StatefulRedisConnection<String, String> existing = connection;
        if (existing != null && existing.isOpen()) {
            return existing;
        }
        synchronized (lock) {
            if (connection != null && connection.isOpen()) {
                return connection;
            }
            try {
                if (client == null) {
                    client = RedisClient.create(uri());
                }
                connection = client.connect();
                return connection;
            } catch (RuntimeException ex) {
                log.warn("Redis 连接失败，降级 Caffeine/直连爬虫: {}", ex.toString());
                skipUntilMs = System.currentTimeMillis() + FAILURE_COOLDOWN_MS;
                invalidateLocked();
                return null;
            }
        }
    }

    private RedisURI uri() {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(settings.host())
                .withPort(settings.port())
                .withDatabase(settings.database())
                .withTimeout(Duration.ofMillis(settings.timeoutMs()));
        if (settings.hasPassword()) {
            builder.withPassword(settings.password().toCharArray());
        }
        return builder.build();
    }

    private boolean coolingDown() {
        return System.currentTimeMillis() < skipUntilMs;
    }

    private void markFailure() {
        skipUntilMs = System.currentTimeMillis() + FAILURE_COOLDOWN_MS;
        invalidate();
    }

    private void invalidate() {
        synchronized (lock) {
            invalidateLocked();
        }
    }

    private void invalidateLocked() {
        if (connection != null) {
            try {
                connection.close();
            } catch (RuntimeException ignored) {
                // ignore
            }
            connection = null;
        }
    }
}
