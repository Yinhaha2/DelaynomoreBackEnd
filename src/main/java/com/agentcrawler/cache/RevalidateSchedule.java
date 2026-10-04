package com.agentcrawler.cache;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 软过期时间：基础 TTL 再加上一段随机抖动，避免同一时刻集中重爬。
 */
public final class RevalidateSchedule {
    private final boolean enabled;
    private final long ttlMillis;
    private final long jitterMillis;

    private RevalidateSchedule(boolean enabled, long ttlMillis, long jitterMillis) {
        this.enabled = enabled;
        this.ttlMillis = Math.max(0, ttlMillis);
        this.jitterMillis = Math.max(0, jitterMillis);
    }

    public static RevalidateSchedule disabled() {
        return new RevalidateSchedule(false, 0, 0);
    }

    public static RevalidateSchedule of(long ttlMillis, long jitterMillis) {
        if (ttlMillis <= 0) {
            return disabled();
        }
        return new RevalidateSchedule(true, ttlMillis, jitterMillis);
    }

    public boolean enabled() {
        return enabled;
    }

    public long next(long now) {
        return now + ttlMillis + jitter();
    }

    /** 探测没有明确结论时，缩短下次校验，而不是立刻当失效删掉。 */
    public long soon(long now) {
        long quarter = Math.max(60_000L, ttlMillis / 4);
        long span = Math.min(quarter, Math.max(jitterMillis, 60_000L));
        return now + span;
    }

    private long jitter() {
        if (jitterMillis <= 0) {
            return 0;
        }
        return ThreadLocalRandom.current().nextLong(jitterMillis + 1);
    }
}
