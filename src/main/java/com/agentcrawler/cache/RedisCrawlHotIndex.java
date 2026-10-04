package com.agentcrawler.cache;

import com.agentcrawler.config.CrawlerCacheProperties;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hash 存目录和大小，ZSet 以最近访问时间做分数。查询和节流更新放在同一条 Lua 里，一次往返。
 */
public final class RedisCrawlHotIndex implements CrawlHotIndex {
    private static final Logger log = LoggerFactory.getLogger(RedisCrawlHotIndex.class);
    private static final long FAILURE_COOLDOWN_MS = 5_000;
    private static final long LOCK_TTL_MS = 120_000;

    private static final String TOUCH = """
            local meta = redis.call('HGET', KEYS[1], ARGV[1])
            if not meta then
              return {'', '0'}
            end
            local score = redis.call('ZSCORE', KEYS[2], ARGV[1])
            local now = tonumber(ARGV[2])
            local window = tonumber(ARGV[3])
            local touched = 0
            if (not score) or (now - tonumber(score) >= window) then
              redis.call('ZADD', KEYS[2], now, ARGV[1])
              touched = 1
            end
            return {meta, tostring(touched)}
            """;

    private static final String UPSERT = """
            local old = redis.call('HGET', KEYS[1], ARGV[1])
            local oldBytes = 0
            if old then
              oldBytes = tonumber(string.match(old, '^(%-?%d+)')) or 0
            end
            local newBytes = tonumber(ARGV[2])
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2] .. ':' .. ARGV[4])
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[1])
            local used = redis.call('HINCRBY', KEYS[3], 'used', newBytes - oldBytes)
            if tonumber(used) < 0 then
              redis.call('HSET', KEYS[3], 'used', '0')
            end
            return 1
            """;

    private static final String REMOVE = """
            local old = redis.call('HGET', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            if not old then
              return 0
            end
            local oldBytes = tonumber(string.match(old, '^(%-?%d+)')) or 0
            redis.call('HDEL', KEYS[1], ARGV[1])
            local used = redis.call('HINCRBY', KEYS[3], 'used', -oldBytes)
            if tonumber(used) < 0 then
              redis.call('HSET', KEYS[3], 'used', '0')
            end
            return 1
            """;

    private static final String UPDATE_REVALIDATE = """
            local old = redis.call('HGET', KEYS[1], ARGV[1])
            if not old then
              return 0
            end
            local bytes = string.match(old, '^(%-?%d+)')
            if not bytes then
              return 0
            end
            redis.call('HSET', KEYS[1], ARGV[1], bytes .. ':' .. ARGV[2])
            return 1
            """;

    private static final String UNLOCK = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private final CrawlerCacheProperties.Redis settings;
    private final String dirKey;
    private final String lruKey;
    private final String statsKey;
    private final String lockKey;
    private final Object lock = new Object();
    private volatile RedisClient client;
    private volatile StatefulRedisConnection<String, String> connection;
    private volatile long skipUntilMs;
    private volatile String lockToken;

    public RedisCrawlHotIndex(CrawlerCacheProperties.Redis settings, String keyPrefix) {
        this.settings = settings;
        String prefix = keyPrefix == null || keyPrefix.isBlank() ? "acrawl" : keyPrefix;
        this.dirKey = prefix + ":cos:dir";
        this.lruKey = prefix + ":cos:lru";
        this.statsKey = prefix + ":cos:stats";
        this.lockKey = prefix + ":cos:evict-lock";
        log.info("对象缓存热索引使用 Redis {}:{} 键 {} / {}", settings.host(), settings.port(), dirKey, lruKey);
    }

    @Override
    public boolean available() {
        return !coolingDown();
    }

    @Override
    public boolean isEmpty() {
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return false;
        }
        try {
            Long size = commands.hlen(dirKey);
            return size == null || size == 0;
        } catch (RuntimeException ex) {
            markFailure("HLEN", ex);
            return false;
        }
    }

    @Override
    public Touch touch(String id, long nowMillis) {
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return Touch.unavailable();
        }
        try {
            Object raw = commands.eval(
                    TOUCH,
                    ScriptOutputType.MULTI,
                    new String[]{dirKey, lruKey},
                    id,
                    Long.toString(nowMillis),
                    Long.toString(TOUCH_WINDOW_MS)
            );
            if (!(raw instanceof List<?> rows) || rows.size() < 2) {
                return Touch.missing();
            }
            String meta = String.valueOf(rows.get(0));
            if (meta.isBlank() || "nil".equals(meta)) {
                return Touch.missing();
            }
            long revalidateAt = parseMeta(meta).revalidateAt;
            boolean updated = "1".equals(String.valueOf(rows.get(1)));
            return new Touch(updated ? Touch.Status.UPDATED : Touch.Status.THROTTLED, revalidateAt);
        } catch (RuntimeException ex) {
            markFailure("TOUCH", ex);
            return Touch.unavailable();
        }
    }

    @Override
    public boolean upsert(String id, long bytes, long lastAccessEpochMs, long revalidateAtEpochMs) {
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return false;
        }
        try {
            commands.eval(
                    UPSERT,
                    ScriptOutputType.INTEGER,
                    new String[]{dirKey, lruKey, statsKey},
                    id,
                    Long.toString(bytes),
                    Long.toString(lastAccessEpochMs),
                    Long.toString(revalidateAtEpochMs)
            );
            return true;
        } catch (RuntimeException ex) {
            markFailure("UPSERT", ex);
            return false;
        }
    }

    @Override
    public void updateRevalidateAt(String id, long revalidateAtEpochMs) {
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return;
        }
        try {
            commands.eval(
                    UPDATE_REVALIDATE,
                    ScriptOutputType.INTEGER,
                    new String[]{dirKey},
                    id,
                    Long.toString(revalidateAtEpochMs)
            );
        } catch (RuntimeException ex) {
            markFailure("REVALIDATE", ex);
        }
    }

    @Override
    public boolean remove(String id) {
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return false;
        }
        try {
            Object removed = commands.eval(
                    REMOVE,
                    ScriptOutputType.INTEGER,
                    new String[]{dirKey, lruKey, statsKey},
                    id
            );
            return removed instanceof Number number && number.longValue() > 0;
        } catch (RuntimeException ex) {
            markFailure("REMOVE", ex);
            return false;
        }
    }

    @Override
    public long usedBytes() {
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return 0;
        }
        try {
            String raw = commands.hget(statsKey, "used");
            if (raw == null || raw.isBlank()) {
                return 0;
            }
            return Math.max(0, Long.parseLong(raw.trim()));
        } catch (RuntimeException ex) {
            markFailure("USED", ex);
            return 0;
        }
    }

    @Override
    public long count() {
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return 0;
        }
        try {
            Long size = commands.hlen(dirKey);
            return size == null ? 0 : size;
        } catch (RuntimeException ex) {
            markFailure("COUNT", ex);
            return 0;
        }
    }

    @Override
    public List<HotEntry> oldest(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return List.of();
        }
        try {
            List<ScoredValue<String>> scored = commands.zrangeWithScores(lruKey, 0, limit - 1L);
            if (scored == null || scored.isEmpty()) {
                return List.of();
            }
            String[] ids = scored.stream().map(ScoredValue::getValue).toArray(String[]::new);
            List<KeyValue<String, String>> metas = commands.hmget(dirKey, ids);
            List<HotEntry> oldest = new ArrayList<>(scored.size());
            for (int i = 0; i < scored.size(); i++) {
                ScoredValue<String> score = scored.get(i);
                String meta = metaAt(metas, i);
                Parsed parsed = parseMeta(meta);
                oldest.add(new HotEntry(score.getValue(), parsed.bytes, (long) score.getScore(), parsed.revalidateAt));
            }
            return oldest;
        } catch (RuntimeException ex) {
            markFailure("ZRANGE", ex);
            return List.of();
        }
    }

    @Override
    public void rebuild(List<HotEntry> entries) {
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return;
        }
        try {
            commands.del(dirKey, lruKey);
            long total = 0;
            if (entries != null) {
                for (HotEntry entry : entries) {
                    if (entry == null || entry.id() == null || entry.id().isBlank()) {
                        continue;
                    }
                    commands.hset(dirKey, entry.id(), meta(entry.bytes(), entry.revalidateAtEpochMs()));
                    commands.zadd(lruKey, entry.lastAccessEpochMs(), entry.id());
                    total += Math.max(0, entry.bytes());
                }
            }
            commands.hset(statsKey, "used", Long.toString(total));
        } catch (RuntimeException ex) {
            markFailure("REBUILD", ex);
        }
    }

    @Override
    public List<HotEntry> snapshot() {
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return null;
        }
        try {
            Map<String, String> directory = commands.hgetall(dirKey);
            if (directory == null || directory.isEmpty()) {
                return List.of();
            }
            List<ScoredValue<String>> scored = commands.zrangeWithScores(lruKey, 0, -1);
            Map<String, Long> scores = new java.util.HashMap<>();
            if (scored != null) {
                for (ScoredValue<String> item : scored) {
                    scores.put(item.getValue(), (long) item.getScore());
                }
            }
            List<HotEntry> entries = new ArrayList<>(directory.size());
            for (Map.Entry<String, String> item : directory.entrySet()) {
                Parsed parsed = parseMeta(item.getValue());
                long seen = scores.getOrDefault(item.getKey(), 0L);
                entries.add(new HotEntry(item.getKey(), parsed.bytes, seen, parsed.revalidateAt));
            }
            return entries;
        } catch (RuntimeException ex) {
            markFailure("SNAPSHOT", ex);
            return null;
        }
    }

    @Override
    public boolean tryEvictLock() {
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return false;
        }
        String token = UUID.randomUUID().toString();
        try {
            String result = commands.set(lockKey, token, io.lettuce.core.SetArgs.Builder.nx().px(LOCK_TTL_MS));
            if ("OK".equalsIgnoreCase(result)) {
                lockToken = token;
                return true;
            }
            return false;
        } catch (RuntimeException ex) {
            markFailure("LOCK", ex);
            return false;
        }
    }

    @Override
    public void unlockEvict() {
        String token = lockToken;
        lockToken = null;
        if (token == null) {
            return;
        }
        RedisCommands<String, String> commands = commands();
        if (commands == null) {
            return;
        }
        try {
            commands.eval(UNLOCK, ScriptOutputType.INTEGER, new String[]{lockKey}, token);
        } catch (RuntimeException ex) {
            markFailure("UNLOCK", ex);
        }
    }

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

    private static String meta(long bytes, long revalidateAt) {
        return bytes + ":" + revalidateAt;
    }

    private static Parsed parseMeta(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Parsed(0, 0);
        }
        int cut = raw.indexOf(':');
        try {
            if (cut < 0) {
                return new Parsed(Long.parseLong(raw.trim()), 0);
            }
            return new Parsed(Long.parseLong(raw.substring(0, cut)), Long.parseLong(raw.substring(cut + 1)));
        } catch (NumberFormatException ex) {
            return new Parsed(0, 0);
        }
    }

    private static String metaAt(List<KeyValue<String, String>> metas, int index) {
        if (metas == null || index >= metas.size()) {
            return "";
        }
        KeyValue<String, String> item = metas.get(index);
        if (item == null || !item.hasValue()) {
            return "";
        }
        return item.getValue();
    }

    private RedisCommands<String, String> commands() {
        if (coolingDown()) {
            return null;
        }
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
                log.warn("对象缓存热索引连接失败，改用对象存储索引: {}", ex.toString());
                skipUntilMs = System.currentTimeMillis() + FAILURE_COOLDOWN_MS;
                closeConnection();
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

    private void markFailure(String op, RuntimeException ex) {
        log.warn("对象缓存热索引 {} 失败，改用对象存储索引: {}", op, ex.toString());
        skipUntilMs = System.currentTimeMillis() + FAILURE_COOLDOWN_MS;
        synchronized (lock) {
            closeConnection();
        }
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (RuntimeException ignored) {
                // ignore
            }
            connection = null;
        }
    }

    private record Parsed(long bytes, long revalidateAt) {
    }
}
